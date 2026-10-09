package com.study.tracker.service;

import com.study.tracker.dao.UserDao;
import com.study.tracker.model.User;
import com.study.tracker.model.UserRole;
import com.study.tracker.util.PasswordUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户与登录业务。
 * <p>
 * 负责用户名校验、密码加盐哈希、登录验证、注册、改密与管理员操作。
 * 所有密码相关处理都在这里完成，DAO 只负责存取。
 */
public class UserService {

    /** 用户名长度下限。 */
    private static final int USERNAME_MIN = 2;

    /** 用户名长度上限。 */
    private static final int USERNAME_MAX = 20;

    /** 密码最短长度。 */
    private static final int PASSWORD_MIN = 6;

    private final UserDao userDao = new UserDao();

    /** 是否还没有任何设置过密码的账户，即首次使用。 */
    public boolean needsInitialAdmin() {
        return userDao.countWithPassword() == 0;
    }

    /** 全部用户，供管理员页面显示。 */
    public List<User> listUsers() {
        return userDao.findAll();
    }

    /**
     * 首次使用时把最早创建的账户初始化为管理员。
     * <p>
     * 数据库初始化阶段会先建一个占位账户，已有学习数据都挂在它名下，
     * 因此这里选择"复用"而不是新建，避免旧数据变成无主记录。
     *
     * @param username 管理员用户名
     * @param password 密码
     * @param confirm  确认密码
     * @return 初始化后的管理员
     */
    public User initializeAdmin(String username, String password, String confirm) {
        List<User> existing = userDao.findAll();
        int legacyId = existing.isEmpty() ? 0 : existing.get(0).getId();

        String name = validateUsername(username, legacyId);
        validatePassword(password, confirm);

        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);

        if (existing.isEmpty()) {
            User admin = new User(name, hash, salt, UserRole.ADMIN);
            return userDao.findById(userDao.insert(admin));
        }

        User legacy = existing.get(0);
        userDao.updateCredentials(legacy.getId(), name, hash, salt, UserRole.ADMIN);
        return userDao.findById(legacy.getId());
    }

    /**
     * 登录校验。
     *
     * @return 登录成功的用户
     * @throws IllegalArgumentException 用户名或密码错误、账户被停用时抛出
     */
    public User login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }

        User user = userDao.findByUsername(name);
        if (user == null || !PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码不正确");
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("该账户已被停用，请联系管理员");
        }

        userDao.updateLastLogin(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        return user;
    }

    /**
     * 注册普通用户。
     *
     * @return 新注册的用户
     */
    public User register(String username, String password, String confirm) {
        String name = validateUsername(username, 0);
        validatePassword(password, confirm);

        String salt = PasswordUtil.newSalt();
        User user = new User(name, PasswordUtil.hash(password, salt), salt, UserRole.USER);
        return userDao.findById(userDao.insert(user));
    }

    /** 修改自己的密码，需要验证原密码。 */
    public void changePassword(User user, String oldPassword, String newPassword, String confirm) {
        if (user == null) {
            throw new IllegalArgumentException("尚未登录");
        }
        User stored = userDao.findById(user.getId());
        if (stored == null || !PasswordUtil.verify(oldPassword, stored.getSalt(), stored.getPasswordHash())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        validatePassword(newPassword, confirm);

        String salt = PasswordUtil.newSalt();
        userDao.updatePassword(user.getId(), PasswordUtil.hash(newPassword, salt), salt);
    }

    /** 管理员重置指定用户的密码。 */
    public void resetPassword(User operator, int userId, String newPassword, String confirm) {
        requireAdmin(operator);
        validatePassword(newPassword, confirm);

        String salt = PasswordUtil.newSalt();
        userDao.updatePassword(userId, PasswordUtil.hash(newPassword, salt), salt);
    }

    /** 管理员启用或停用账户。 */
    public void setEnabled(User operator, int userId, boolean enabled) {
        requireAdmin(operator);
        if (!enabled && operator.getId() == userId) {
            throw new IllegalArgumentException("不能停用当前登录的账户");
        }
        userDao.updateEnabled(userId, enabled);
    }

    /** 管理员新增账户。 */
    public User createUser(User operator, String username, String password, String confirm, UserRole role) {
        requireAdmin(operator);
        String name = validateUsername(username, 0);
        validatePassword(password, confirm);

        String salt = PasswordUtil.newSalt();
        User created = new User(name, PasswordUtil.hash(password, salt), salt,
                role == null ? UserRole.USER : role);
        return userDao.findById(userDao.insert(created));
    }

    private void requireAdmin(User operator) {
        if (operator == null || operator.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("只有管理员可以执行该操作");
        }
    }

    /** 校验用户名；excludeId 用于"沿用已有账户"时排除自己。 */
    private String validateUsername(String username, int excludeId) {
        String name = username == null ? "" : username.trim();
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            throw new IllegalArgumentException(
                    "用户名长度需在 " + USERNAME_MIN + "～" + USERNAME_MAX + " 个字符之间");
        }
        if (name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("用户名不能包含空格");
        }
        if (userDao.existsUsername(name, excludeId)) {
            throw new IllegalArgumentException("用户名 " + name + " 已被占用");
        }
        return name;
    }

    private void validatePassword(String password, String confirm) {
        if (password == null || password.length() < PASSWORD_MIN) {
            throw new IllegalArgumentException("密码至少需要 " + PASSWORD_MIN + " 位");
        }
        if (!password.equals(confirm)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
    }
}
