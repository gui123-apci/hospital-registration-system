package com.hospital.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.entity.Patient;
import com.hospital.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 历史数据迁移：注册流程早期会自动创建"姓名=手机号"的就诊人，
 * 这些脏数据导致医生端显示手机号而非真实姓名。
 *
 * 策略：启动时把姓名是手机格式的就诊人标记为"待完善"，
 * 前端就诊人列表据此显示警告标签，引导用户主动修改。
 * 订单表已有 patient_name 快照，改名不会影响历史订单显示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientDataMigration implements CommandLineRunner {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final PatientMapper patientMapper;

    @Override
    public void run(String... args) {
        List<Patient> dirty = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                .isNotNull(Patient::getName));
        int migrated = 0;
        for (Patient patient : dirty) {
            String name = patient.getName();
            if (name != null && PHONE_PATTERN.matcher(name).matches()) {
                patientMapper.update(null, new LambdaUpdateWrapper<Patient>()
                        .eq(Patient::getId, patient.getId())
                        .set(Patient::getName, "待完善"));
                migrated++;
            }
        }
        if (migrated > 0) {
            log.warn("历史数据迁移：{} 个就诊人姓名=手机号，已标记为「待完善」，请引导用户修改", migrated);
        }
    }
}
