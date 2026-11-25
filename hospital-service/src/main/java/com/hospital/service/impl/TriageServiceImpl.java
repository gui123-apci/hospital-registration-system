package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.client.AiClient;
import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.common.constant.TriageConstants;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.triage.TriageAskDTO;
import com.hospital.entity.Department;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.service.TriageService;
import com.hospital.vo.triage.TriageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 导诊实现。五层结构，层层是保险丝：
 * 限流（防连点）→ 缓存（省调用）→ 模型（理解症状）→ 回库校验（真不真数据库说了算）→ 关键词兜底（模型挂了还能给结果）。
 *
 * 核心认知：模型是"理解层"，不是"事实源"。
 * 它只负责把症状翻译成一个候选科室名，名字真不真由数据库精确校验说了算——
 * 和挂号里"Redis 只削峰、正确性靠数据库"是同一个结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {

    private final AiClient aiClient;
    private final DepartmentMapper departmentMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 关键词兜底：症状关键词 → 科室名"片段"（回库用 like 匹配）。
     * 故意不写死完整科室名——科室名是运营通过管理端配置的，代码写死精确名必然对不上；
     * 用"片段 + like"对运营数据做最小耦合（心内科/心血管内科都能被"心内"命中）。
     * 用 LinkedHashMap 保证匹配顺序，具体词放前面。
     */
    private static final Map<String, String> KEYWORD_DEPARTMENT = new LinkedHashMap<>();

    static {
        KEYWORD_DEPARTMENT.put("胸痛", "心内");
        KEYWORD_DEPARTMENT.put("心慌", "心内");
        KEYWORD_DEPARTMENT.put("心悸", "心内");
        KEYWORD_DEPARTMENT.put("胸闷", "心内");
        KEYWORD_DEPARTMENT.put("咳嗽", "呼吸");
        KEYWORD_DEPARTMENT.put("发烧", "呼吸");
        KEYWORD_DEPARTMENT.put("发热", "呼吸");
        KEYWORD_DEPARTMENT.put("感冒", "呼吸");
        KEYWORD_DEPARTMENT.put("腹痛", "消化");
        KEYWORD_DEPARTMENT.put("腹泻", "消化");
        KEYWORD_DEPARTMENT.put("胃", "消化");
        KEYWORD_DEPARTMENT.put("头痛", "神经");
        KEYWORD_DEPARTMENT.put("头晕", "神经");
        KEYWORD_DEPARTMENT.put("骨折", "骨");
        KEYWORD_DEPARTMENT.put("腰", "骨");
        KEYWORD_DEPARTMENT.put("关节", "骨");
        KEYWORD_DEPARTMENT.put("皮肤", "皮肤");
        KEYWORD_DEPARTMENT.put("过敏", "皮肤");
        KEYWORD_DEPARTMENT.put("皮疹", "皮肤");
        KEYWORD_DEPARTMENT.put("眼睛", "眼");
        KEYWORD_DEPARTMENT.put("视力", "眼");
        KEYWORD_DEPARTMENT.put("牙", "口腔");
        KEYWORD_DEPARTMENT.put("口腔", "口腔");
        KEYWORD_DEPARTMENT.put("耳朵", "耳鼻");
        KEYWORD_DEPARTMENT.put("鼻", "耳鼻");
        KEYWORD_DEPARTMENT.put("咽喉", "耳鼻");
        KEYWORD_DEPARTMENT.put("妇科", "妇");
        KEYWORD_DEPARTMENT.put("月经", "妇");
        KEYWORD_DEPARTMENT.put("怀孕", "产");
    }

    @Override
    public TriageVO ask(TriageAskDTO dto, Long userId) {
        String symptoms = dto.getSymptoms().trim();

        // 1. 限流：同一用户 5 秒内只放一个，防连点/刷新重复调模型（前端禁用按钮管不住抓包直连）。
        //    Redis 挂了跳过限流继续——限流是优化不是正确性底线，别把"服务不可用"误报成"手速太快"
        if (isRateLimited(userId)) {
            throw new BusinessException(ResultCode.TRIAGE_TOO_FREQUENT);
        }

        // 2. 查缓存：同一症状 24 小时内命中，省一次模型调用；Redis 异常当作未命中
        String cacheKey = RedisKeyConstants.TRIAGE_RESULT_PREFIX + md5(symptoms);
        TriageVO cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 3. 模型推荐 → 回库校验；模型失败/科室对不上 → 关键词兜底
        TriageVO vo = tryAi(symptoms);
        if (vo == null) {
            vo = fallbackByKeyword(symptoms);
        }

        // 4. 写缓存：只缓存 AI 结果——关键词兜底是模型故障时的临时答案，
        //    不该被放大成 24 小时"真相"，否则模型恢复后命中缓存的请求永远用不上模型
        if (TriageConstants.SOURCE_AI.equals(vo.getSource())) {
            putCache(cacheKey, vo);
        }
        return vo;
    }

    /**
     * 尝试走模型：拼 prompt（含科室列表）→ 调用 → 解析 JSON → 回库精确校验。
     * 任何一步失败都返回 null，交给关键词兜底，绝不把异常抛给患者。
     */
    private TriageVO tryAi(String symptoms) {
        try {
            // 一次查全科室：既拼 prompt，又供回库校验复用，避免每请求两次 DB
            List<Department> depts = departmentMapper.selectList(null);
            if (depts.isEmpty()) {
                // 库里连科室都没有，模型也推荐不出真实科室，直接走关键词（可能也兜不住）
                return null;
            }
            List<String> deptNames = depts.stream().map(Department::getName).toList();
            String text = aiClient.chat(buildSystemPrompt(deptNames), symptoms);
            return parseAiResult(text, depts);
        } catch (Exception e) {
            log.warn("AI 导诊异常，降级关键词兜底: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析模型输出：剥掉可能的 ```json 围栏 → 读 department 字段 → 回库精确校验。
     * 名字对不上返回 null，触发关键词兜底——模型编的名字不落库。
     */
    private TriageVO parseAiResult(String text, List<Department> depts) {
        //空文本放弃
        if (text == null || text.isBlank()) {
            return null;
        }
        //剥掉json外壳
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            //解析提取三个字段
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            String deptName = node.path("department").asText("");
            String reason = node.path("reason").asText("");
            boolean urgent = node.path("urgent").asBoolean(false);
            if (deptName.isBlank()) {
                return null;
            }
            // 回库精确校验：从已查的科室列表里精确匹配，模型编的名字不落库
            Department dept = depts.stream()
                    .filter(d -> d.getName().equals(deptName))
                    .findFirst()
                    .orElse(null);
            if (dept == null) {
                log.warn("模型返回科室名不存在，转入关键词兜底: {}", deptName);
                return null;
            }
            return buildVo(dept.getId(), dept.getName(), reason, urgent, TriageConstants.SOURCE_AI);
        } catch (Exception e) {
            log.warn("模型输出解析失败，转入关键词兜底: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 关键词兜底：症状命中关键词 → 片段 like 回库查第一个科室。
     * 关键词命中了但库里没对应科室，就继续找下一个关键词；全兜不住才报"无法判断"。
     */
    private TriageVO fallbackByKeyword(String symptoms) {
        for (Map.Entry<String, String> entry : KEYWORD_DEPARTMENT.entrySet()) {
            if (symptoms.contains(entry.getKey())) {
                Department dept = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                        .like(Department::getName, entry.getValue())
                        .last("LIMIT 1"));
                if (dept != null) {
                    return buildVo(dept.getId(), dept.getName(),
                            "根据症状关键词匹配推荐，仅供参考，请以医生诊断为准",
                            false, TriageConstants.SOURCE_RULE);
                }
            }
        }
        throw new BusinessException(ResultCode.TRIAGE_NO_MATCH);
    }

    /** system prompt：角色 + 只能选列表内科室 + 严格 JSON 输出。 */
    private String buildSystemPrompt(List<String> deptNames) {
        return "你是医院导诊助手。请根据患者描述的症状，从下面科室列表中选择最合适的一个科室。" +
                "只能选择列表中的科室名称，严禁编造列表之外的名字。\n" +
                "科室列表：" + String.join("、", deptNames) + "\n" +
                "只输出如下 JSON，不要输出任何其他文字或解释：\n" +
                "{\"department\":\"科室名\",\"reason\":\"一句话推荐理由\",\"urgent\":false}\n" +
                "urgent 表示是否建议急诊：呼吸困难、剧烈胸痛、意识模糊等危险症状为 true，否则 false。";
    }

    private TriageVO buildVo(Long id, String name, String reason, boolean urgent, String source) {
        TriageVO vo = new TriageVO();
        vo.setDepartmentId(id);
        vo.setDepartmentName(name);
        vo.setReason(reason);
        vo.setUrgent(urgent);
        vo.setSource(source);
        return vo;
    }

    /** 限流判断：只有明确 false（key 已存在）才是真频繁；null（异常边界）/抛异常都放行降级。 */
    private boolean isRateLimited(Long userId) {
        String lockKey = RedisKeyConstants.TRIAGE_LOCK_PREFIX + userId;
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", RedisKeyConstants.TRIAGE_LOCK_TTL);
            return Boolean.FALSE.equals(locked);
        } catch (Exception e) {
            log.warn("导诊限流检查失败，跳过限流: {}", e.getMessage());
            return false;
        }
    }

    /** 读缓存：命中返回 VO（source 置 CACHE）；Redis 异常/缓存损坏都当作未命中。 */
    private TriageVO getCached(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            TriageVO vo = objectMapper.readValue(json, TriageVO.class);
            if (vo == null) {
                stringRedisTemplate.delete(cacheKey);
                return null;
            }
            vo.setSource(TriageConstants.SOURCE_CACHE);
            return vo;
        } catch (Exception e) {
            log.warn("导诊缓存读取失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    /** 写缓存：失败忽略，不影响主流程（缓存是优化，不是正确性底线）。 */
    private void putCache(String cacheKey, TriageVO vo) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(vo), RedisKeyConstants.TRIAGE_RESULT_TTL);
        } catch (Exception e) {
            log.warn("导诊缓存写入失败，忽略: {}", e.getMessage());
        }
    }

    /** 文本 → MD5 指纹，作为缓存 key。入参已由 ask() 统一 trim 过，避免"心慌"和"心慌 "两个 key。 */
    private String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }
}
