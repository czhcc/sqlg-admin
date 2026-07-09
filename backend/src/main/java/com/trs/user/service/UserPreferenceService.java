package com.trs.user.service;

import com.trs.user.entity.UserPreference;
import com.trs.user.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPreferenceService {

    public static final String KEY_TOPOLOGY_ACTIVE_CONNECTION = "topology.active_connection";
    public static final String KEY_VERTEX_TYPE_ACTIVE_CONNECTION = "vertex_type.active_connection";
    public static final String KEY_EDGE_TYPE_ACTIVE_CONNECTION = "edge_type.active_connection";
    public static final String KEY_PROPERTY_MANAGEMENT_ACTIVE_CONNECTION = "property_management.active_connection";
    public static final String KEY_VERTEX_DATA_ACTIVE_CONNECTION = "vertex_data.active_connection";
    public static final String KEY_EDGE_DATA_ACTIVE_CONNECTION = "edge_data.active_connection";

    private final UserPreferenceMapper mapper;

    public UserPreferenceService(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    public String get(Long userId, String key) {
        UserPreference p = mapper.select(userId, key);
        return p == null ? null : p.getPrefValue();
    }

    public Long getAsLong(Long userId, String key) {
        String v = get(userId, key);
        if (v == null || v.isBlank()) return null;
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void set(Long userId, String key, String value) {
        mapper.upsert(userId, key, value);
    }

    public void set(Long userId, String key, Long value) {
        mapper.upsert(userId, key, value == null ? null : String.valueOf(value));
    }
}
