package com.trs.user.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体,对应 sys_role 表。
 *
 * @author czh
 * @date 2026/0715
 */
@Data
public class Role implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleKey;
    private String roleName;
    private String description;
    private Short status;
    private Boolean isBuiltin;
    private String menuPermissions;
    private String operationPermissions;
    private String gremlinPermission;
    private String dangerousPermissions;
    private String connectionDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
