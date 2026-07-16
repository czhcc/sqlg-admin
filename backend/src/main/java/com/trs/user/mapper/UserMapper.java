package com.trs.user.mapper;

import com.trs.user.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/0714
 */
public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    List<User> selectPage(@Param("offset") int offset,
                           @Param("size") int size,
                           @Param("keyword") String keyword,
                           @Param("status") Short status);

    long selectCount(@Param("keyword") String keyword,
                      @Param("status") Short status);

    int insert(User user);

    int update(User user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    int updateRoles(@Param("id") Long id, @Param("roles") String roles);

    int updateLastLoginTime(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    int countByUsername(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * 查询所有用户(排除 admin),供权限总览搜索使用。
     *
     * @param keyword 用户名/昵称/邮箱关键词
     * @param status  状态过滤
     * @return 用户列表
     */
    List<User> selectAllForOverview(@Param("keyword") String keyword, @Param("status") Short status);
}
