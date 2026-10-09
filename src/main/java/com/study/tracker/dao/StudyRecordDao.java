package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.RecordSource;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.TimerMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习记录数据访问对象。
 * <p>
 * 所有查询都通过 JOIN 带出科目名称，避免界面层再做一次查询。
 * 时间统一以 {@code yyyy-MM-dd HH:mm:ss} 文本存储，便于使用 SQLite 的 date() 函数做分组统计。
 */
public class StudyRecordDao {

    private static final String SELECT_WITH_SUBJECT =
            "SELECT r.id, r.user_id, r.subject_id, s.name AS subject_name, "
          + "r.start_time, r.end_time, r.duration_minutes, r.paused_seconds, r.pause_count, "
          + "r.mode, r.completed, r.source, r.remark, r.created_at "
          + "FROM study_record r LEFT JOIN subject s ON r.subject_id = s.id ";

    /**
     * 新增学习记录。
     *
     * @return 新记录的主键
     */
    public int insert(StudyRecord record) {
        String sql = "INSERT INTO study_record "
                + "(user_id, subject_id, start_time, end_time, duration_minutes, paused_seconds, "
                + "pause_count, mode, completed, source, remark) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, record.getUserId());
            ps.setInt(2, record.getSubjectId());
            ps.setString(3, format(record.getStartTime()));
            ps.setString(4, format(record.getEndTime()));
            ps.setInt(5, record.getDurationMinutes());
            ps.setInt(6, record.getPausedSeconds());
            ps.setInt(7, record.getPauseCount());
            ps.setString(8, record.getMode().name());
            ps.setInt(9, record.isCompleted() ? 1 : 0);
            ps.setString(10, record.getSource().name());
            ps.setString(11, record.getRemark());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    record.setId(id);
                    return id;
                }
            }
            throw new IllegalStateException("新增学习记录后未能获取主键");
        } catch (SQLException e) {
            throw new IllegalStateException("新增学习记录失败: " + e.getMessage(), e);
        }
    }

    /** 更新学习记录（用于编辑已有记录）。 */
    public void update(StudyRecord record) {
        String sql = "UPDATE study_record SET subject_id = ?, start_time = ?, end_time = ?, "
                + "duration_minutes = ?, remark = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, record.getSubjectId());
            ps.setString(2, format(record.getStartTime()));
            ps.setString(3, format(record.getEndTime()));
            ps.setInt(4, record.getDurationMinutes());
            ps.setString(5, record.getRemark());
            ps.setInt(6, record.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新学习记录失败: " + e.getMessage(), e);
        }
    }

    /** 按主键删除学习记录。 */
    public void delete(int id) {
        String sql = "DELETE FROM study_record WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除学习记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按条件查询学习记录。
     *
     * @param userId    用户 ID
     * @param from      起始日期，可为 {@code null}
     * @param to        结束日期，可为 {@code null}
     * @param subjectId 科目 ID，可为 {@code null} 表示不限
     * @return 按开始时间倒序排列的记录列表
     */
    public List<StudyRecord> search(int userId, LocalDate from, LocalDate to, Integer subjectId) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_SUBJECT);
        sql.append("WHERE r.user_id = ? ");
        if (from != null) {
            sql.append("AND date(r.start_time) >= ? ");
        }
        if (to != null) {
            sql.append("AND date(r.start_time) <= ? ");
        }
        if (subjectId != null) {
            sql.append("AND r.subject_id = ? ");
        }
        sql.append("ORDER BY r.start_time DESC");

        List<StudyRecord> records = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setInt(index++, userId);
            if (from != null) {
                ps.setString(index++, from.format(AppConfig.DATE_FORMATTER));
            }
            if (to != null) {
                ps.setString(index++, to.format(AppConfig.DATE_FORMATTER));
            }
            if (subjectId != null) {
                ps.setInt(index, subjectId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询学习记录失败: " + e.getMessage(), e);
        }
        return records;
    }

    /** 统计指定日期范围内的学习总时长（分钟）。 */
    public int sumDuration(int userId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM study_record WHERE user_id = ? ");
        if (from != null) {
            sql.append("AND date(start_time) >= ? ");
        }
        if (to != null) {
            sql.append("AND date(start_time) <= ? ");
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setInt(index++, userId);
            if (from != null) {
                ps.setString(index++, from.format(AppConfig.DATE_FORMATTER));
            }
            if (to != null) {
                ps.setString(index, to.format(AppConfig.DATE_FORMATTER));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计学习时长失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按天统计学习时长。
     * <p>
     * 使用 SQL 的 GROUP BY 完成聚合，避免把全表数据读入内存后循环计算。
     *
     * @return 日期 → 分钟数（按日期升序）
     */
    public Map<LocalDate, Integer> sumDurationByDay(int userId, LocalDate from, LocalDate to) {
        String sql = "SELECT date(start_time) AS d, SUM(duration_minutes) AS total "
                + "FROM study_record WHERE user_id = ? "
                + "AND date(start_time) >= ? AND date(start_time) <= ? "
                + "GROUP BY date(start_time) ORDER BY d ASC";

        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(LocalDate.parse(rs.getString("d"), AppConfig.DATE_FORMATTER), rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("按天统计学习时长失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 按科目统计学习时长。
     *
     * @return 科目名称 → 分钟数（按时长降序）
     */
    public Map<String, Integer> sumDurationBySubject(int userId, LocalDate from, LocalDate to) {
        String sql = "SELECT s.name AS subject_name, SUM(r.duration_minutes) AS total "
                + "FROM study_record r LEFT JOIN subject s ON r.subject_id = s.id "
                + "WHERE r.user_id = ? AND date(r.start_time) >= ? AND date(r.start_time) <= ? "
                + "GROUP BY r.subject_id ORDER BY total DESC";

        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("subject_name");
                    result.put(name == null ? "未分类" : name, rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("按科目统计学习时长失败: " + e.getMessage(), e);
        }
        return result;
    }

    /** 将结果集当前行映射为学习记录对象。 */
    private StudyRecord mapRow(ResultSet rs) throws SQLException {
        StudyRecord record = new StudyRecord();
        record.setId(rs.getInt("id"));
        record.setUserId(rs.getInt("user_id"));
        record.setSubjectId(rs.getInt("subject_id"));
        record.setSubjectName(rs.getString("subject_name"));
        record.setStartTime(parseDateTime(rs.getString("start_time")));
        record.setEndTime(parseDateTime(rs.getString("end_time")));
        record.setDurationMinutes(rs.getInt("duration_minutes"));
        record.setPausedSeconds(rs.getInt("paused_seconds"));
        record.setPauseCount(rs.getInt("pause_count"));
        record.setMode(TimerMode.valueOf(rs.getString("mode")));
        record.setCompleted(rs.getInt("completed") == 1);
        record.setSource(RecordSource.valueOf(rs.getString("source")));
        record.setRemark(rs.getString("remark"));
        record.setCreatedAt(parseDateTime(rs.getString("created_at")));
        return record;
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(AppConfig.DATE_TIME_FORMATTER);
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text, AppConfig.DATE_TIME_FORMATTER);
    }
    /**
     * 统计指定日期范围内、指定科目的学习总时长（分钟）。
     *
     * @param subjectId 科目 ID，传 {@code null} 表示不限科目
     */
    public int sumDuration(int userId, LocalDate from, LocalDate to, Integer subjectId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(duration_minutes), 0) FROM study_record WHERE user_id = ? ");
        if (from != null) {
            sql.append("AND date(start_time) >= ? ");
        }
        if (to != null) {
            sql.append("AND date(start_time) <= ? ");
        }
        if (subjectId != null) {
            sql.append("AND subject_id = ? ");
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setInt(index++, userId);
            if (from != null) {
                ps.setString(index++, from.format(AppConfig.DATE_FORMATTER));
            }
            if (to != null) {
                ps.setString(index++, to.format(AppConfig.DATE_FORMATTER));
            }
            if (subjectId != null) {
                ps.setInt(index, subjectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计学习时长失败: " + e.getMessage(), e);
        }
    }
}