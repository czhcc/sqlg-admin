package com.trs.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private List<T> rows;

    public static <T> PageResult<T> of(long total, List<T> rows) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(total);
        p.setRows(rows);
        return p;
    }
}
