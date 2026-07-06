package com.trs.user.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private Short status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
