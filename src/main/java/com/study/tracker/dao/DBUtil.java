package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.UserRole;
import com.study.tracker.util.PathUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库工具类。
 * <p>
 * 负责三件事：
 * <ol>
 *   <li>解析数据库文件位置并建立连接（数据库文件位于程序目录下的 data 文件夹）；</li>
 *   <li>首次启动时读取 classpath 中的 schema.sql 并执行建表语句；</li>
 *   <li>确保存在默认账户，使带外键约束的业务数据能够正常写入。</li>
 * </ol>
 * 所有建表语句均使用 {@code CREATE TABLE IF NOT EXISTS}，因此重复执行是安全的。
 */
public final class DBUtil {

    /** JDBC 连接串前缀。 */
    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    /** 当前数据库连接串，初始化后赋值。 */
    private static volatile String jdbcUrl;

    private DBUtil() {
    }

    /**
     * 初始化数据库：创建数据目录、执行建表脚本、确保默认账户存在。
     * <p>
     * 必须在调用 {@link #getConnection()} 之前执行一次。
     */
    public static void initialize() {
        try {
            Path dataDir = PathUtil.getDataDir();
            Path dbFile = dataDir.resolve(AppConfig.DATABASE_FILE_NAME);
            jdbcUrl = JDBC_PREFIX + dbFile.toAbsolutePath();

            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                executeSchema(conn);
                ensureDefaultUser(conn);
            }
            System.out.println("[DBUtil] 数据库已就绪: " + dbFile.toAbsolutePath());
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据库连接。
     * <p>
     * 每次调用返回一个新连接，使用后请用 try-with-resources 关闭。
     *
     * @return 数据库连接
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        if (jdbcUrl == null) {
            throw new IllegalStateException("数据库尚未初始化，请先调用 DBUtil.initialize()");
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        // SQLite 默认不启用外键约束，需按连接开启
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /** 执行建表脚本。 */
    private static void executeSchema(Connection conn) throws SQLException, IOException {
        for (String sql : splitStatements(readSchema())) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    /**
     * 确保存在默认账户。
     * <p>
     * study_record、task、goal 等表都通过外键引用 sys_user(id)，
     * 若该表为空则任何业务数据都无法写入，因此首次启动时必须创建一条用户记录。
     * 登录注册功能完成后，此处将由"首次启动引导注册管理员"取代。
     */
    private static void ensureDefaultUser(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sys_user")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        String sql = "INSERT INTO sys_user (username, password_hash, salt, role, enabled) VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, AppConfig.DEFAULT_USERNAME);
            ps.setString(2, "");
            ps.setString(3, "");
            ps.setString(4, UserRole.ADMIN.name());
            ps.executeUpdate();
        }
        System.out.println("[DBUtil] 已创建默认账户: " + AppConfig.DEFAULT_USERNAME);
    }

    /** 从 classpath 读取建表脚本内容。 */
    private static String readSchema() throws IOException {
        try (InputStream in = DBUtil.class.getResourceAsStream(AppConfig.SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("找不到建表脚本: " + AppConfig.SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 按分号拆分 SQL 脚本。
     * <p>
     * JDBC 的 {@code Statement} 一次只能执行一条语句，因此需要拆分；
     * 同时跳过以 {@code --} 开头的纯注释行，避免产生空语句。
     */
    private static List<String> splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }

        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String sql = part.trim();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }
}