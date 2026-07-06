package com.trs.user.service;

import com.trs.user.entity.User;
import com.trs.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    public boolean checkPassword(User user, String rawPassword) {
        return user != null
                && user.getStatus() != null
                && user.getStatus() == 1
                && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public void changePassword(Long id, String rawPassword) {
        userMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
    }
}
