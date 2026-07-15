package com.trs.modules.log.service;

import com.trs.modules.log.entity.LoginLog;
import com.trs.modules.log.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录日志服务,提供登录日志写入和查询能力。
 *
 * @author czh
 * @date 2026/0714
 */
@Service
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    public Map<String, Object> page(int page, int size, String username, String resultStatus,
                                      LocalDateTime startTime, LocalDateTime endTime, String keyword) {
        if (size > 200) size = 200;
        int offset = (page - 1) * size;

        List<LoginLog> rows = loginLogMapper.selectPage(offset, size, username, resultStatus,
                startTime, endTime, keyword);
        long total = loginLogMapper.selectCount(username, resultStatus, startTime, endTime, keyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public void recordSuccess(Long userId, String username) {
        LoginLog entry = new LoginLog();
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setResultStatus("SUCCESS");
        fillRequestInfo(entry);
        loginLogMapper.insert(entry);
    }

    public void recordFailure(String username, String failReason) {
        LoginLog entry = new LoginLog();
        entry.setUsername(username);
        entry.setResultStatus("FAILED");
        entry.setFailReason(failReason);
        fillRequestInfo(entry);
        loginLogMapper.insert(entry);
    }

    private void fillRequestInfo(LoginLog entry) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            entry.setClientIp(getClientIp(request));
            String ua = request.getHeader("User-Agent");
            if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
            entry.setUserAgent(ua);
        }
    }

    private static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip;
    }
}
