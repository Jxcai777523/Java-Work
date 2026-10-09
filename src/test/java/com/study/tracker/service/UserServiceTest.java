package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.DBUtil;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.model.User;
import com.study.tracker.model.UserRole;
import com.study.tracker.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("用户与登录业务")
class UserServiceTest {

    private final UserService userService = new UserService();

    @BeforeEach
    void setUp() {
        TestDatabase.reset("user");
    }

    @Test
    @DisplayName("全新数据库需要初始化管理员")
    void needsInitialAdminOnFreshDatabase() {
        assertTrue(userService.needsInitialAdmin());
    }

    @Test
    @DisplayName("初始化管理员会复用原占位账户，历史学习记录不会丢")
    void initializeAdminKeepsExistingData() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO subject (name, color, weekly_target_minutes) "
                    + "VALUES ('历史科目', '#111111', 60)");
            st.executeUpdate("INSERT INTO study_record "
                    + "(user_id, subject_id, start_time, end_time, duration_minutes) "
                    + "VALUES (1, 1, '2026-01-01 09:00:00', '2026-01-01 10:00:00', 60)");
        }

        User admin = userService.initializeAdmin("admin", "secret123", "secret123");
        assertEquals(1, admin.getId());
        assertEquals(UserRole.ADMIN, admin.getRole());

        UserSession.login(admin);
        assertEquals(1, new StudyRecordDao()
                .search(UserSession.currentUserId(), null, null, null).size());
    }

    @Test
    @DisplayName("密码错误或账户不存在时不能登录")
    void loginRejectsWrongCredentials() {
        userService.initializeAdmin("admin", "secret123", "secret123");

        assertNotNull(userService.login("admin", "secret123"));
        assertThrows(IllegalArgumentException.class, () -> userService.login("admin", "wrong"));
        assertThrows(IllegalArgumentException.class, () -> userService.login("nobody", "secret123"));
        assertThrows(IllegalArgumentException.class, () -> userService.login("admin", ""));
    }

    @Test
    @DisplayName("被停用的账户不能登录")
    void disabledAccountCannotLogin() {
        User admin = userService.initializeAdmin("admin", "secret123", "secret123");
        User student = userService.register("student", "abc123", "abc123");

        userService.setEnabled(admin, student.getId(), false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> userService.login("student", "abc123"));
        assertTrue(error.getMessage().contains("停用"));
    }

    @Test
    @DisplayName("注册会校验用户名唯一性与长度、密码长度与一致性")
    void registerValidatesInput() {
        userService.initializeAdmin("admin", "secret123", "secret123");

        assertNotNull(userService.register("student", "abc123", "abc123"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("student", "abc123", "abc123"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("a", "abc123", "abc123"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("hi there", "abc123", "abc123"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("tester1", "123", "123"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("tester1", "abc123", "abc124"));
    }

    @Test
    @DisplayName("非管理员不能执行管理操作，管理员不能停用自己")
    void adminOperationsAreProtected() {
        User admin = userService.initializeAdmin("admin", "secret123", "secret123");
        User student = userService.register("student", "abc123", "abc123");

        assertThrows(IllegalArgumentException.class,
                () -> userService.setEnabled(student, admin.getId(), false));
        assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(student, "newbie", "abc123", "abc123", UserRole.USER));
        assertThrows(IllegalArgumentException.class,
                () -> userService.setEnabled(admin, admin.getId(), false));
    }

    @Test
    @DisplayName("修改密码需要原密码正确，改完后可用新密码登录")
    void changePasswordRequiresCorrectOldPassword() {
        User admin = userService.initializeAdmin("admin", "secret123", "secret123");

        assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(admin, "wrong", "newpass456", "newpass456"));

        userService.changePassword(admin, "secret123", "newpass456", "newpass456");
        assertNotNull(userService.login("admin", "newpass456"));
        assertThrows(IllegalArgumentException.class, () -> userService.login("admin", "secret123"));
    }
}
