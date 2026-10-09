package com.study.tracker.config;

import com.study.tracker.model.User;
import com.study.tracker.model.UserRole;

/**
 * 当前登录会话。
 * <p>
 * 保存已登录用户，供业务层获取"当前用户 ID"，替代早期写死的默认用户。
 * 桌面程序只有一个窗口，因此用静态字段保存唯一会话。
 */
public final class UserSession {

    private static User current;

    private UserSession() {
    }

    /**
     * 记录登录用户。
     *
     * @param user 登录成功的用户
     */
    public static void login(User user) {
        current = user;
    }

    /** 清除登录状态。 */
    public static void logout() {
        current = null;
    }

    /** 当前登录用户；未登录时为 null。 */
    public static User current() {
        return current;
    }

    /** 是否已登录。 */
    public static boolean isLoggedIn() {
        return current != null;
    }

    /**
     * 当前登录用户 ID。
     *
     * @return 用户 ID
     * @throws IllegalStateException 尚未登录时抛出
     */
    public static int currentUserId() {
        if (current == null) {
            throw new IllegalStateException("尚未登录，无法获取当前用户");
        }
        return current.getId();
    }

    /** 当前用户是否为管理员。 */
    public static boolean isAdmin() {
        return current != null && current.getRole() == UserRole.ADMIN;
    }
}
