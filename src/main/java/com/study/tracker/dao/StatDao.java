package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统计分析专用数据访问对象。
 * <p>
 * 与 {@link StudyRecordDao} 的区别：这里只做"聚合计算"，
 * 把 SUM / COUNT / GROUP BY 等运算交给数据库完成，避免把大量明细读入内存。
 */
public class StatDao {

    /**
     * 查询所有存在学习记录的日期（用于计算连续学习天数）。
     *
     * @return 日期集合，按时间升序
     */
    public Set<LocalDate> findStudyDates(int userId) {
        String sql = "SELECT DISTINCT date(start_time) AS d FROM study_record "
                + "WHERE user_id = ? ORDER BY d ASC";

        Set<LocalDate> dates = new LinkedHashSet<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dates.add(LocalDate.parse(rs.getString("d"), AppConfig.DATE_FORMATTER));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询学习日期失败: " + e.getMessage(), e);
        }
        return dates;
    }

    /**
     * 统计番茄钟记录数量。
     *
     * @return 长度为 2 的数组：下标 0 为番茄钟记录总数，下标 1 为完整完成数
     */
    public int[] countPomodoro(int userId, LocalDate from, LocalDate to) {
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0) AS done "
                + "FROM study_record WHERE user_id = ? AND mode = 'POMODORO' "
                + "AND date(start_time) >= ? AND date(start_time) <= ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt("total"), rs.getInt("done")};
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计番茄钟失败: " + e.getMessage(), e);
        }
        return new int[]{0, 0};
    }

    /** 统计记录条数。 */
    public int countRecords(int userId, LocalDate from, LocalDate to) {
        return countRecords(userId, from, to, 0);
    }

    /**
     * 统计时长不低于指定分钟数的记录条数。
     *
     * @param minMinutes 最小分钟数，低于该值的记录视为碎片时间
     */
    public int countRecords(int userId, LocalDate from, LocalDate to, int minMinutes) {
        String sql = "SELECT COUNT(*) FROM study_record WHERE user_id = ? "
                + "AND date(start_time) >= ? AND date(start_time) <= ? AND duration_minutes >= ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            ps.setInt(4, minMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计记录条数失败: " + e.getMessage(), e);
        }
    }

    /** 统计暂停（中断）总次数。 */
    public int sumPauseCount(int userId, LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(pause_count), 0) FROM study_record WHERE user_id = ? "
                + "AND date(start_time) >= ? AND date(start_time) <= ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计中断次数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按小时统计学习时长，用于"高效时段分析"。
     *
     * @return 小时（0~23）→ 分钟数
     */
    public Map<Integer, Integer> sumMinutesByHour(int userId, LocalDate from, LocalDate to) {
        String sql = "SELECT CAST(strftime('%H', start_time) AS INTEGER) AS h, "
                + "SUM(duration_minutes) AS total FROM study_record WHERE user_id = ? "
                + "AND date(start_time) >= ? AND date(start_time) <= ? GROUP BY h ORDER BY h";

        Map<Integer, Integer> result = new LinkedHashMap<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, to.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("h"), rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("按时段统计失败: " + e.getMessage(), e);
        }
        return result;
    }
}