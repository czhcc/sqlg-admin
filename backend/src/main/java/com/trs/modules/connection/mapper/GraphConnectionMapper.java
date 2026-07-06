package com.trs.modules.connection.mapper;

import com.trs.modules.connection.entity.GraphConnection;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GraphConnectionMapper {

    List<GraphConnection> selectAll(@Param("keyword") String keyword);

    GraphConnection selectById(@Param("id") Long id);

    GraphConnection selectByName(@Param("name") String name);

    int insert(GraphConnection c);

    int update(GraphConnection c);

    int updateWithoutPassword(GraphConnection c);

    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    int clearAllDefault();

    int setDefault(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
