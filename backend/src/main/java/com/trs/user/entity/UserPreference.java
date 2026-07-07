package com.trs.user.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserPreference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String prefKey;
    private String prefValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
