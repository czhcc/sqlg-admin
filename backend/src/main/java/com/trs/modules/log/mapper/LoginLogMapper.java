package com.trs.modules.log.mapper;

import com.trs.modules.log.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/0714
 */
@Mapper
public interface LoginLogMapper {

    void insert(LoginLog log);

    List<LoginLog> selectPage(@Param("offset") int offset,
                               @Param("size") int size,
                               @Param("username") String username,
                               @Param("resultStatus") String resultStatus,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("keyword") String keyword);

    long selectCount(@Param("username") String username,
                      @Param("resultStatus") String resultStatus,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime,
                      @Param("keyword") String keyword);

    List<LoginLog> selectByUserId(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countByUserId(@Param("userId") Long userId);
}
