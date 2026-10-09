package com.study.tracker.config;

import java.time.format.DateTimeFormatter;

/**
 * 全局配置常量。
 * <p>
 * 集中存放应用名称、文件路径、日期格式等不随运行环境变化的配置项，
 * 避免在代码各处出现"魔法值"。
 */
public final class AppConfig {

    /** 应用英文名（用于窗口类名、打包等）。 */
    public static final String APP_NAME = "StudyTimeTracker";

    /** 应用中文标题。 */
    public static final String APP_TITLE = "个人学习时间管理与效率分析系统";

    /** 应用版本号。 */
    public static final String APP_VERSION = "1.0.0";

    /** 数据目录名，位于程序运行目录下。 */
    public static final String DATA_DIR_NAME = "data";

    /** 数据库文件名。 */
    public static final String DATABASE_FILE_NAME = "data.db";

    /** 建表脚本在 classpath 中的位置。 */
    public static final String SCHEMA_RESOURCE = "/db/schema.sql";

    /**
     * 数据库初始化时创建的占位账户名。
     * <p>
     * 该账户没有密码，仅用于保证外键有效；首次启动时会被引导设置成管理员账号。
     */
    public static final String DEFAULT_USERNAME = "default";

    /** 日期格式（yyyy-MM-dd）。 */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日期时间格式（yyyy-MM-dd HH:mm:ss）。 */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 界面默认字体名称。 */
    public static final String UI_FONT_NAME = "微软雅黑";

    private AppConfig() {
    }
}