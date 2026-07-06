package com.trs.modules.connection.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GraphConnection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String dbType;
    private String jdbcUrl;
    private String username;
    private String password;
    private Boolean distributed;
    private String poolConfig;
    private String remark;
    private Short status;
    private Boolean isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
