package com.hospital;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.doctor.DoctorCreateDTO;
import com.hospital.entity.Doctor;
import com.hospital.entity.User;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.service.DoctorService;
import com.hospital.vo.doctor.DoctorVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新增医生时自动开户的集成测试：
 * tb_user(role=2) 与 tb_doctor 在同一事务内创建并互相绑定，医生能直接用手机号登录。
 * 连真实 MySQL，测试账号用随机手机号并在结束后清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DoctorAccountTest {

    private static final String INITIAL_PASSWORD = "admin123";

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DoctorMapper doctorMapper;

    @Autowired
    private MockMvc mockMvc;

    /** 每个用例用不同手机号，避免和历史数据冲突 */
    private final String phone = "137" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);

    @AfterEach
    void cleanup() {
        User account = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, phone));
        if (account == null) return;
        // 先删医生再删账号：医生记录通过 user_id 引用账号
        doctorMapper.delete(new LambdaQueryWrapper<Doctor>().eq(Doctor::getUserId, account.getId()));
        userMapper.deleteById(account.getId());
    }

    private DoctorCreateDTO buildDto(String phone) {
        DoctorCreateDTO dto = new DoctorCreateDTO();
        dto.setDepartmentId(1L);
        dto.setName("测试医生");
        dto.setTitle("主治医师");
        dto.setPhone(phone);
        return dto;
    }

    @Test
    void createDoctorAlsoCreatesLoginAccount() throws Exception {
        DoctorVO created = doctorService.create(buildDto(phone));
        assertThat(created.getId()).isNotNull();

        // 1. 账号建出来了，角色是医生，手机号就是登录名
        User account = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, phone));
        assertThat(account).isNotNull();
        assertThat(account.getRole()).isEqualTo(2);
        assertThat(account.getPhone()).isEqualTo(phone);

        // 2. 医生记录反指这个账号（绑定关系成立）
        Doctor doctor = doctorMapper.selectById(created.getId());
        assertThat(doctor.getUserId()).isEqualTo(account.getId());

        // 3. 用手机号 + 初始密码能直接登录，说明密码哈希是有效的
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + phone + "\",\"password\":\"" + INITIAL_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void duplicatePhoneRejectedWithoutCreatingOrphanAccount() {
        doctorService.create(buildDto(phone));
        User account = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, phone));

        assertThatThrownBy(() -> doctorService.create(buildDto(phone)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResultCode.USERNAME_EXISTS.getCode());

        // 只允许留下一条账号和一位绑定它的医生，不产生孤儿数据
        Long accountCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, phone));
        Long doctorCount = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>().eq(Doctor::getUserId, account.getId()));
        assertThat(accountCount).isEqualTo(1L);
        assertThat(doctorCount).isEqualTo(1L);
    }
}
