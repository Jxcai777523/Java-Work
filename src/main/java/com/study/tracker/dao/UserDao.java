package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.User;
import com.study.tracker.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据访问对象。
 * <p>
 * 只负责 sys_user 表的读写，密码加密与业务校验在 UserService 中完成。
 */
public class UserDao {

    private static final String COLUMNS =
            "id, username, password_hash, salt, role, enabled, created_at, last_login_at";

    /** 查询全部用户，按创建顺序排列。 */
    public List<User> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM sys_user ORDER BY id ASC";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户列表失败: " + e.getMessage(), e);
        }
        return users;
    }

    /** 按用户名查询。 */
    public User findByUsername(String username) {
        String sql = "SELECT " + COLUMNS + " FROM sys_user WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户失败: " + e.getMessage(), e);
        }
    }

    /** 按主键查询。 */
    public User findById(int id) {
        String sql = "SELECT " + COLUMNS + " FROM sys_user WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户失败: " + e.getMessage(), e);
        }
    }

    /**
     * 新增用户。
     *
     * @return 新用户的主键
     */
    public int insert(User user) {
        String sql = "INSERT INTO sys_user (username, password_hash, salt, role, enabled) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getRole().name());
            ps.setInt(5, user.isEnabled() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("新增用户失败: " + e.getMessage(), e);
        }
    }

    /** 修改用户名、密码摘要与角色（用于首次初始化管理员）。 */
    public void updateCredentials(int id, String username, String passwordHash, String salt, UserRole role) {
        String sql = "UPDATE sys_user SET username = ?, password_hash = ?, salt = ?, role = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setString(4, role.name());
            ps.setInt(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新用户失败: " + e.getMessage(), e);
        }
    }

    /** 只修改密码摘要。 */
    public void updatePassword(int id, String passwordHash, String salt) {
        String sql = "UPDATE sys_user SET password_hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setString(2, salt);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改密码失败: " + e.getMessage(), e);
        }
    }

    /** 修改启用状态。 */
    public void updateEnabled(int id, boolean enabled) {
        String sql = "UPDATE sys_user SET enabled = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改用户状态失败: " + e.getMessage(), e);
        }
    }

    /** 记录最近一次登录时间。 */
    public void updateLastLogin(int id) {
        String sql = "UPDATE sys_user SET last_login_at = datetime('now', 'localtime') WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新登录时间失败: " + e.getMessage(), e);
        }
    }

    /** 已设置密码的账户数量，用于判断是否属于首次使用。 */
    public int countWithPassword() {
        String sql = "SELECT COUNT(*) FROM sys_user WHERE password_hash IS NOT NULL AND password_hash <> ''";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("统计用户数量失败: " + e.getMessage(), e);
        }
    }

    /** 用户名是否已被占用（可排除某个用户 ID）。 */
    public boolean existsUsername(String username, int excludeId) {
        String sql = "SELECT COUNT(*) FROM sys_user WHERE username = ? AND id <> ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验用户名失败: " + e.getMessage(), e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setEnabled(rs.getInt("enabled") == 1);
        user.setCreatedAt(parseDateTime(rs.getString("created_at")));
        user.setLastLoginAt(parseDateTime(rs.getString("last_login_at")));
        return user;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text, AppConfig.DATE_TIME_FORMATTER);
    }
}
