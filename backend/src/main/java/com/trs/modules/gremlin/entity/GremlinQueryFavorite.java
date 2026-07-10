package com.trs.modules.gremlin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Gremlin 收藏查询实体。
 *
 * @author czh
 * @date 2026/07/10
 */
@Data
public class GremlinQueryFavorite {
    private Long id;
    private Long userId;
    private String title;
    private String queryText;
    private String description;
    private String mode;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
