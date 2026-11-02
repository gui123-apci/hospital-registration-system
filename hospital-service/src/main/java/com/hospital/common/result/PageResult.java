package com.hospital.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 分页返回结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public static <S, T> PageResult<T> of(IPage<S> page, Function<S, T> converter) {
        return new PageResult<>(page.getTotal(), page.getRecords().stream().map(converter).toList());
    }
}
