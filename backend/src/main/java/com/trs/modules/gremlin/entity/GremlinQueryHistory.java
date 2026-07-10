package com.trs.modules.gremlin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Gremlin 查询历史记录实体。
 *
 * @author czh
 * @date 2026/07/10
 */
@Data
public class GremlinQueryHistory {
    private Long id;
    private Long userId;
    private Long connectionId;
    private String queryText;
    private String mode;
    private Boolean success;
    private String errorMessage;
    private Integer costMs;
    private Integer resultCount;
    private LocalDateTime createTime;
}
