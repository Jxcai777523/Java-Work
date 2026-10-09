package com.study.tracker.model;

import java.time.LocalDateTime;

/**
 * 用户。
 * <p>
 * 对应数据库表 {@code sys_user}。密码不以明文存储，
 * 而是保存 {@code SHA-256(明文密码 + 随机盐)} 的结果与随机盐本身。
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private UserRole role = UserRole.USER;
    private boolean enabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public User() {
    }

    public User(String username, String passwordHash, String salt, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** 返回用户名，便于下拉框等组件直接显示。 */
    @Override
    public String toString() {
        return username;
    }
}