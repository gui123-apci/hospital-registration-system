package com.hospital.vo.auth;

import com.hospital.common.enums.UserRole;
import com.hospital.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回给前端的用户信息，不包含密码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    private Long id;
    private String username;
    private String phone;

    /** 角色值：0患者 1管理员 2医生 */
    private Integer role;

    /** 角色中文名，前端直接展示 */
    private String roleName;

    public static UserInfoVO from(User user) {
        UserRole userRole = UserRole.of(user.getRole());
        return new UserInfoVO(
                user.getId(),
                user.getUsername(),
                user.getPhone(),
                userRole.getCode(),
                userRole.getDesc());
    }
}
