package com.trs.modules.propertyManagement.mapper;

import com.trs.modules.propertyManagement.entity.PropertyMeta;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 属性 UI 元数据 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/07/08
 */
public interface PropertyMetaMapper {

    /**
     * 按 连接 + label 维度查询所有属性元数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @return 元数据列表
     */
    List<PropertyMeta> selectByLabel(@Param("connectionId") Long connectionId,
                                     @Param("labelKind") String labelKind,
                                     @Param("schemaName") String schemaName,
                                     @Param("labelName") String labelName);

    /**
     * 查询单条元数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 属性名
     * @return 元数据,不存在返回 null
     */
    PropertyMeta select(@Param("connectionId") Long connectionId,
                        @Param("labelKind") String labelKind,
                        @Param("schemaName") String schemaName,
                        @Param("labelName") String labelName,
                        @Param("propertyName") String propertyName);

    /**
     * 新增或更新元数据 (UPSERT)。
     *
     * @param meta 元数据
     * @return 影响行数
     */
    int upsert(PropertyMeta meta);

    /**
     * 删除单条元数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 属性名
     * @return 影响行数
     */
    int delete(@Param("connectionId") Long connectionId,
               @Param("labelKind") String labelKind,
               @Param("schemaName") String schemaName,
               @Param("labelName") String labelName,
               @Param("propertyName") String propertyName);
}
