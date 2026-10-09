package com.study.tracker.util;

import com.study.tracker.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径解析工具。
 * <p>
 * 负责确定数据目录与数据库文件的位置，并处理一个实际存在的兼容性问题：
 * 在部分 Windows 账户下（用户名含特殊字符），JVM 的 user.home 会被错误地
 * 解析为 C:\，导致数据文件写入失败。因此这里优先使用 USERPROFILE 环境变量，
 * 并按 "用户显式指定 → 程序运行目录 → 用户主目录" 的顺序逐级回退。
 */
public final class PathUtil {

    /** 系统属性名：用于显式指定数据目录。 */
    public static final String PROP_DATA_DIR = "stt.data.dir";

    /** 环境变量名：用于显式指定数据目录。 */
    public static final String ENV_DATA_DIR = "STT_DATA_DIR";

    /** 回退数据目录名（放在用户主目录下时使用）。 */
    private static final String FALLBACK_DIR_NAME = ".study-tracker";

    private PathUtil() {
    }

    /**
     * 返回数据目录路径，若不存在则自动创建。
     *
     * @return 数据目录
     * @throws IOException 目录创建失败时抛出
     */
    public static Path getDataDir() throws IOException {
        Path dir = resolveDataDir();
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * 返回数据库文件路径（不保证文件已存在）。
     *
     * @return 数据库文件路径
     */
    public static Path getDatabaseFile() {
        return resolveDataDir().resolve(AppConfig.DATABASE_FILE_NAME);
    }

    /**
     * 解析用户主目录。
     * <p>
     * 优先读取 USERPROFILE 环境变量，其次才是 JVM 的 user.home 属性。
     *
     * @return 用户主目录
     */
    public static Path resolveUserHome() {
        String profile = System.getenv("USERPROFILE");
        if (isNotBlank(profile)) {
            return Paths.get(profile).toAbsolutePath().normalize();
        }
        String home = System.getProperty("user.home");
        if (isNotBlank(home)) {
            return Paths.get(home).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("java.io.tmpdir", ".")).toAbsolutePath().normalize();
    }

    /** 按优先级解析数据目录，不做创建操作。 */
    private static Path resolveDataDir() {
        // 1. 优先使用显式指定的目录（便于测试与多环境部署）
        String custom = firstNonBlank(System.getProperty(PROP_DATA_DIR), System.getenv(ENV_DATA_DIR));
        if (custom != null) {
            return Paths.get(custom).toAbsolutePath().normalize();
        }

        // 2. 程序运行目录下的 data 子目录（开发时为项目根目录，打包后为程序所在目录）
        Path appDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (Files.isWritable(appDir)) {
            return appDir.resolve(AppConfig.DATA_DIR_NAME);
        }

        // 3. 回退到用户主目录
        return resolveUserHome().resolve(FALLBACK_DIR_NAME);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}