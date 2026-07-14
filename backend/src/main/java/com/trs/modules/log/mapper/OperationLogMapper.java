package com.trs.modules.log.mapper;

import com.trs.modules.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/07/13
 */
@Mapper
public interface OperationLogMapper {

    void insert(OperationLog log);

    OperationLog selectById(@Param("id") Long id);

    List<OperationLog> selectPage(@Param("offset") int offset, @Param("size") int size,
                                   @Param("module") String module,
                                   @Param("operationType") String operationType,
                                   @Param("status") String status,
                                   @Param("connectionId") Long connectionId,
                                   @Param("username") String username,
                                   @Param("isDangerous") Boolean isDangerous,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("keyword") String keyword);

    long selectCount(@Param("module") String module,
                      @Param("operationType") String operationType,
                      @Param("status") String status,
                      @Param("connectionId") Long connectionId,
                      @Param("username") String username,
                      @Param("isDangerous") Boolean isDangerous,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime,
                      @Param("keyword") String keyword);

    List<String> selectDistinctModules();

    void deleteBefore(@Param("before") LocalDateTime before);
}
