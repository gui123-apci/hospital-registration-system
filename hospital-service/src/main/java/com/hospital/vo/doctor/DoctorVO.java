package com.hospital.vo.doctor;

import com.hospital.entity.Doctor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 返回给前端的医生信息。
 * 注意：userId 是内部账号关联，不暴露给前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorVO {

    private Long id;
    private Long departmentId;
    private String name;
    private String title;
    private String intro;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DoctorVO from(Doctor doctor) {
        return new DoctorVO(
                doctor.getId(),
                doctor.getDepartmentId(),
                doctor.getName(),
                doctor.getTitle(),
                doctor.getIntro(),
                doctor.getCreateTime(),
                doctor.getUpdateTime());
    }
}
