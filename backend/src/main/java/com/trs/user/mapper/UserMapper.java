package com.trs.user.mapper;

import com.trs.user.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    int insert(User user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
