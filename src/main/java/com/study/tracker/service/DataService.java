package com.study.tracker.service;

import com.study.tracker.config.AppConfig;
import com.study.tracker.config.UserSession;
import com.study.tracker.dao.DBUtil;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.model.RecordSource;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.TimerMode;
import com.study.tracker.util.PathUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据管理服务：数据库备份、数据库恢复与学习记录导出。
 * <p>
 * 备份使用 SQLite 的 {@code VACUUM INTO}，它会在写出副本的同时整理数据库，
 * 得到的是一个内容一致的独立文件，因此程序运行中也能安全备份。
 * 恢复采用"先备份当前库、再整文件覆盖"的方式，完成后必须重启程序，
 * 避免残留的连接继续使用旧数据。
 */
public class DataService {

    /** SQLite 文件头，用于在打开之前先判断是不是数据库文件。 */
    private static final String SQLITE_HEADER = "SQLite format 3\u0000";

    /** 生成文件名时使用的时间戳格式。 */
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 恢复前自动保存的原库副本所使用的前缀。 */
    private static final String SAFETY_PREFIX = "before-restore-";

    /** 本系统必须具备的数据表，用于校验备份文件是否属于本系统。 */
    private static final String[] REQUIRED_TABLES = {"sys_user", "subject", "study_record", "task", "goal"};

    /**
     * 备份数据库。
     *
     * @param target 备份文件位置；已存在时会被覆盖
     */
    public void backup(File target) {
        if (target == null) {
            throw new IllegalArgumentException("未指定备份文件");
        }

        Path targetPath = target.toPath().toAbsolutePath();
        try {
            // VACUUM INTO 要求目标文件不存在
            Files.deleteIfExists(targetPath);
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, targetPath.toString().replace('\\', '/'));
                ps.execute();
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法写入备份文件: " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new IllegalStateException("备份数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 用备份文件覆盖当前数据库。
     *
     * @param source 备份文件
     * @return 覆盖之前自动保存的原数据库副本路径
     */
    public Path restore(File source) {
        validateBackup(source);

        Path database = PathUtil.getDatabaseFile().toAbsolutePath();
        try {
            Path safety = database.resolveSibling(
                    database.getFileName() + "." + SAFETY_PREFIX + LocalDateTime.now().format(FILE_STAMP));
            if (Files.exists(database)) {
                Files.copy(database, safety, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(source.toPath(), database, StandardCopyOption.REPLACE_EXISTING);

            // 恢复后旧的日志文件已经没有意义，留着会让 SQLite 尝试回滚
            for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
                Files.deleteIfExists(database.resolveSibling(database.getFileName() + suffix));
            }
            return safety;
        } catch (IOException e) {
            throw new IllegalStateException("恢复数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验文件是否为本系统的数据库备份。
     *
     * @param source 待校验文件
     * @throws IllegalArgumentException 文件不是本系统的备份时抛出
     */
    public void validateBackup(File source) {
        if (source == null || !source.isFile()) {
            throw new IllegalArgumentException("备份文件不存在");
        }

        Path database = PathUtil.getDatabaseFile().toAbsolutePath();
        if (source.toPath().toAbsolutePath().equals(database)) {
            throw new IllegalArgumentException("不能选择当前正在使用的数据库文件");
        }

        byte[] header;
        try (InputStream in = new FileInputStream(source)) {
            header = in.readNBytes(SQLITE_HEADER.length());
        } catch (IOException e) {
            throw new IllegalStateException("读取备份文件失败: " + e.getMessage(), e);
        }
        if (!SQLITE_HEADER.equals(new String(header, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("这不是 SQLite 数据库文件");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN (");
        for (int i = 0; i < REQUIRED_TABLES.length; i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + source.getAbsolutePath());
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < REQUIRED_TABLES.length; i++) {
                ps.setString(i + 1, REQUIRED_TABLES[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) < REQUIRED_TABLES.length) {
                    throw new IllegalArgumentException("该数据库缺少本系统必需的数据表，可能不是本系统的备份");
                }
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("无法读取该数据库: " + e.getMessage(), e);
        }
    }

    /**
     * 导出全部学习记录为 CSV 文件。
     * <p>
     * 文件带 UTF-8 BOM，用 Excel 直接打开不会乱码。
     *
     * @param target 目标文件
     * @return 导出的记录条数
     */
    public int exportRecords(File target) {
        if (target == null) {
            throw new IllegalArgumentException("未指定导出文件");
        }

        List<StudyRecord> records = new StudyRecordDao()
                .search(UserSession.currentUserId(), null, null, null);

        try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
            writer.write('﻿');
            writer.write("开始时间,结束时间,科目,时长(分钟),计时模式,记录来源,番茄钟完成,暂停次数,暂停秒数,备注");
            writer.newLine();
            for (StudyRecord record : records) {
                writer.write(String.join(",",
                        csv(formatTime(record.getStartTime())),
                        csv(formatTime(record.getEndTime())),
                        csv(record.getSubjectName()),
                        csv(String.valueOf(record.getDurationMinutes())),
                        csv(modeText(record.getMode())),
                        csv(sourceText(record.getSource())),
                        csv(record.isCompleted() ? "是" : "否"),
                        csv(String.valueOf(record.getPauseCount())),
                        csv(String.valueOf(record.getPausedSeconds())),
                        csv(record.getRemark())));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("导出 CSV 失败: " + e.getMessage(), e);
        }
        return records.size();
    }

    /** 当前数据库文件路径。 */
    public Path databasePath() {
        return PathUtil.getDatabaseFile().toAbsolutePath();
    }

    /** 当前数据库文件大小（字节）；文件不存在时返回 0。 */
    public long databaseSize() {
        Path database = PathUtil.getDatabaseFile();
        try {
            return Files.exists(database) ? Files.size(database) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    /** CSV 字段转义：整体加引号，内部引号翻倍。 */
    private static String csv(String value) {
        String text = value == null ? "" : value;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(AppConfig.DATE_TIME_FORMATTER);
    }

    private static String modeText(TimerMode mode) {
        if (mode == null) {
            return "";
        }
        return mode == TimerMode.POMODORO ? "番茄钟" : "自由计时";
    }

    private static String sourceText(RecordSource source) {
        if (source == null) {
            return "";
        }
        return source == RecordSource.MANUAL ? "手动补录" : "自动计时";
    }
}
