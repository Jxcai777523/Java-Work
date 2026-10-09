package com.study.tracker.model;

/**
 * 用户角色。
 */
public enum UserRole {

    /** 普通用户：只能操作自己的数据。 */
    USER("普通用户"),

    /** 管理员：可查看全体统计、管理用户与数据。 */
    ADMIN("管理员");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    /** 返回用于界面显示的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}