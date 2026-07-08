package com.trs.modules.propertyManagement.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图库属性 UI 元数据实体,对应 sys_property_meta 表。
 * <p>sqlg 的 topology 不包含显示名称、是否可搜索等 UI 概念,
 * 此实体用于在管理库中补充这些信息。
 *
 * @author czh
 * @date 2026/07/08
 */
@Data
public class PropertyMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long connectionId;
    private String labelKind;
    private String schemaName;
    private String labelName;
    private String propertyName;
    private String displayName;
    private Integer isSearchable;
    private Integer isListDisplay;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
