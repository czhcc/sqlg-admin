package com.trs.modules.gremlin.mapper;

import com.trs.modules.gremlin.entity.GremlinQueryFavorite;
import com.trs.modules.gremlin.entity.GremlinQueryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Gremlin 查询历史与收藏的 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/07/10
 */
@Mapper
public interface GremlinQueryMapper {

    List<GremlinQueryHistory> selectHistory(@Param("userId") Long userId, @Param("connectionId") Long connectionId, @Param("limit") int limit);

    void insertHistory(GremlinQueryHistory history);

    void deleteHistory(@Param("id") Long id, @Param("userId") Long userId);

    void clearHistory(@Param("userId") Long userId, @Param("connectionId") Long connectionId);

    List<GremlinQueryFavorite> selectFavorites(@Param("userId") Long userId);

    void insertFavorite(GremlinQueryFavorite favorite);

    void updateFavorite(GremlinQueryFavorite favorite);

    void deleteFavorite(@Param("id") Long id, @Param("userId") Long userId);
}
