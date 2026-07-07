package com.trs.user.mapper;

import com.trs.user.entity.UserPreference;
import org.apache.ibatis.annotations.Param;

public interface UserPreferenceMapper {

    UserPreference select(@Param("userId") Long userId, @Param("prefKey") String prefKey);

    int upsert(@Param("userId") Long userId,
               @Param("prefKey") String prefKey,
               @Param("prefValue") String prefValue);
}
