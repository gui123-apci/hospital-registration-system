package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.common.constant.PatientConstants;
import com.hospital.common.enums.Relation;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.patient.PatientCreateDTO;
import com.hospital.dto.patient.PatientUpdateDTO;
import com.hospital.entity.Order;
import com.hospital.entity.Patient;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.service.PatientService;
import com.hospital.vo.patient.PatientVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 就诊人业务实现。
 *
 * 身份证唯一约束：同一账号下身份证唯一（uk_user_id_card）。不同账号可以各自添加同一个
 * 家人（比如父母各自注册，都给孩子建一条就诊人），所以不能做成全局唯一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientMapper patientMapper;
    private final OrderMapper orderMapper;

    @Override
    public List<PatientVO> listMine(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId)
                        .orderByAsc(Patient::getId))
                .stream()
                .map(PatientVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO create(PatientCreateDTO dto, Long userId) {
        // 数量上限：身份证为空的家人不占唯一键位，没有上限就能无限刷垃圾数据
        Long count = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getUserId, userId));
        if (count != null && count >= PatientConstants.MAX_COUNT) {
            throw new BusinessException(ResultCode.PATIENT_LIMIT_EXCEEDED);
        }

        // "本人"只能有一个：注册时已自动创建，再添加 relation=0 会让"我的家人"里出现多个本人
        if (Integer.valueOf(Relation.SELF.getCode()).equals(dto.getRelation())) {
            Long self = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getUserId, userId)
                    .eq(Patient::getRelation, Relation.SELF.getCode()));
            if (self != null && self > 0) {
                throw new BusinessException(ResultCode.PATIENT_SELF_DUPLICATED);
            }
        }

        // 空串归一成 NULL：前端表单没填常提交 ""，"" 会撞唯一键（两条 "" 相等），
        // 报"该身份证已添加"误导用户；NULL 不参与唯一键比较，才是"没填"的正确表达
        String idCard = normalizeIdCard(dto.getIdCard());

        // 预查重给可读提示；并发兜底靠 uk_user_id_card 唯一键
        checkIdCardUnique(userId, idCard, null);

        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName(dto.getName());
        patient.setIdCard(idCard);
        patient.setRelation(dto.getRelation());
        patient.setPhone(dto.getPhone());
        try {
            patientMapper.insert(patient);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ID_CARD_EXISTS);
        }
        return PatientVO.from(patient);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientVO update(PatientUpdateDTO dto, Long userId) {
        Patient exist = patientMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.PATIENT_NOT_FOUND);
        }
        // 只能改自己名下的就诊人，不能改别人的
        if (!exist.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PATIENT_NOT_OWNED);
        }

        // 改成"本人"也一样受限：排除自己后已有别的本人记录就不允许
        if (Integer.valueOf(Relation.SELF.getCode()).equals(dto.getRelation())
                && !Integer.valueOf(Relation.SELF.getCode()).equals(exist.getRelation())) {
            Long self = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getUserId, userId)
                    .eq(Patient::getRelation, Relation.SELF.getCode())
                    .ne(Patient::getId, dto.getId()));
            if (self != null && self > 0) {
                throw new BusinessException(ResultCode.PATIENT_SELF_DUPLICATED);
            }
        }

        // 身份证：null = 不改；空串 = 显式清空（前端把身份证删掉再保存才会传空串）。
        // 格式由 PatientUpdateDTO 的 @Pattern 在入口校验，含 * 的脱敏值会被直接拒绝
        String rawIdCard = dto.getIdCard();
        String idCard = normalizeIdCard(rawIdCard);
        boolean clearIdCard = rawIdCard != null && idCard == null;

        // 身份证变了才查重（排除自己），改别的字段不碰唯一键
        if (idCard != null && !idCard.equals(exist.getIdCard())) {
            checkIdCardUnique(userId, idCard, exist.getId());
        }

        if (dto.getName() != null) {
            exist.setName(dto.getName());
        }
        if (idCard != null) {
            exist.setIdCard(idCard);
        }
        if (dto.getRelation() != null) {
            exist.setRelation(dto.getRelation());
        }
        if (dto.getPhone() != null) {
            exist.setPhone(dto.getPhone());
        }
        try {
            patientMapper.updateById(exist);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ID_CARD_EXISTS);
        }
        // MyBatis-Plus 的 updateById 默认忽略 null 字段，清空必须单独发一条 SET id_card = NULL
        if (clearIdCard) {
            patientMapper.update(null, new LambdaUpdateWrapper<Patient>()
                    .eq(Patient::getId, exist.getId())
                    .set(Patient::getIdCard, null));
            exist.setIdCard(null);
        }
        return PatientVO.from(exist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Patient exist = patientMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.PATIENT_NOT_FOUND);
        }
        if (!exist.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PATIENT_NOT_OWNED);
        }

        // 有挂号记录就不许删：订单是医疗凭证，必须能追溯到就诊人。
        // 物理删除会让 tb_order.patient_id 变成孤儿，历史订单查不到就诊人
        Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPatientId, id));
        if (orderCount != null && orderCount > 0) {
            throw new BusinessException(ResultCode.PATIENT_HAS_ORDER);
        }

        // 物理删除；uk_user_id_card 随行消失，同一身份证之后可以重新添加
        patientMapper.deleteById(id);
    }

    /** 空串/null 归一成 null（没填）；有值原样返回 */
    private String normalizeIdCard(String idCard) {
        return (idCard == null || idCard.isBlank()) ? null : idCard;
    }

    /**
     * 同一账号下身份证是否已存在。idCard 为空跳过（NULL 不参与唯一键比较）。
     *
     * @param excludeId 更新时传自己的 id，把自身排除掉
     */
    private void checkIdCardUnique(Long userId, String idCard, Long excludeId) {
        if (idCard == null) {
            return;
        }
        LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getUserId, userId)
                .eq(Patient::getIdCard, idCard);
        if (excludeId != null) {
            wrapper.ne(Patient::getId, excludeId);
        }
        Long count = patientMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.ID_CARD_EXISTS);
        }
    }
}
