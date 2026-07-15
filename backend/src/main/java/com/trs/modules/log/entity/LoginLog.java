package com.trs.modules.log.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志实体。
 *
 * @author czh
 * @date 2026/0714
 */
@Data
public class LoginLog {
    private Long id;
    private Long userId;
    private String username;
    private LocalDateTime loginTime;
    private LocalDateTime logoutTime;
    private String clientIp;
    private String userAgent;
    private String resultStatus;
    private String failReason;
}
