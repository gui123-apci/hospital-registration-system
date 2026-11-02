package com.hospital;

import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.NumberSourceMapper;
import com.hospital.mapper.OrderMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentMapper;
import com.hospital.mapper.ReportMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 临时冒烟测试：确认 9 张表的实体字段与数据库列完全匹配。
 */
@SpringBootTest
class MapperSmokeTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private DoctorMapper doctorMapper;
    @Autowired
    private ScheduleMapper scheduleMapper;
    @Autowired
    private NumberSourceMapper numberSourceMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private ReportMapper reportMapper;

    @Test
    void allEntityColumnsMatchDatabase() {
        userMapper.selectList(null);
        patientMapper.selectList(null);
        departmentMapper.selectList(null);
        doctorMapper.selectList(null);
        scheduleMapper.selectList(null);
        numberSourceMapper.selectList(null);
        orderMapper.selectList(null);
        paymentMapper.selectList(null);
        reportMapper.selectList(null);
    }
}
