package com.trs.user.mapper;

import com.trs.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/0715
 */
@Mapper
public interface RoleMapper {

    List<Role> selectAll(@Param("keyword") String keyword, @Param("status") Short status);

    Role selectById(@Param("id") Long id);

    Role selectByKey(@Param("roleKey") String roleKey);

    int insert(Role role);

    int update(Role role);

    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    int deleteById(@Param("id") Long id);

    int countUsersByRoleKey(@Param("roleKey") String roleKey);

    List<Long> selectUserIdsByRoleKey(@Param("roleKey") String roleKey);

    int addRoleToUser(@Param("userId") Long userId, @Param("roleKey") String roleKey);

    int removeRoleFromUser(@Param("userId") Long userId, @Param("roleKey") String roleKey);

    java.util.List<java.util.Map<String, Object>> selectConnectionAuth(@Param("roleId") Long roleId);

    /**
     * 批量查询多个角色的连接授权记录,用于连接列表的角色可见性过滤。
     *
     * @param roleIds 角色ID列表
     * @return 每条记录包含 roleId / connectionId / accessLevel
     */
    java.util.List<java.util.Map<String, Object>> selectConnectionAuthForRoles(@Param("roleIds") java.util.List<Long> roleIds);

    int upsertConnectionAuth(@Param("roleId") Long roleId, @Param("connectionId") Long connectionId, @Param("accessLevel") String accessLevel);

    int deleteConnectionAuth(@Param("roleId") Long roleId, @Param("connectionId") Long connectionId);
}
