package com.hospital.common.enums;

import lombok.Getter;

/**
 * 就诊人与账号的关系：0本人 1父母 2子女 3其他。
 * "本人"每个账号只能有一个（注册时自动创建），新增/更新时业务层校验。
 */
@Getter
public enum Relation {

    SELF(0, "本人"),
    PARENT(1, "父母"),
    CHILD(2, "子女"),
    OTHER(3, "其他");

    private final int code;
    private final String desc;

    Relation(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
