package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.Goal;
import com.study.tracker.model.GoalPeriod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习目标数据访问对象。
 * <p>
 * 目标按周期划分（每日 / 每周 / 每月），可选关联具体科目；
 * {@code subject_id} 为空时表示针对全部学习时间的总目标。
 */
public class GoalDao {

    private static final String SELECT_WITH_SUBJECT =
            "SELECT g.id, g.user_id, g.subject_id, s.name AS subject_name, g.period_type, "
          + "g.target_minutes, g.start_date, g.end_date, g.created_at "
          + "FROM goal g LEFT JOIN subject s ON g.subject_id = s.id ";

    /**
     * 查询全部目标。
     *
     * @return 目标列表（按周期、科目排序）
     */
    public List<Goal> findAll(int userId) {
        String sql = SELECT_WITH_SUBJECT
                + "WHERE g.user_id = ? "
                + "ORDER BY CASE g.period_type WHEN 'DAILY' THEN 0 WHEN 'WEEKLY' THEN 1 ELSE 2 END, "
                + "CASE WHEN g.subject_id IS NULL THEN 0 ELSE 1 END, g.id";

        List<Goal> goals = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    goals.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询学习目标失败: " + e.getMessage(), e);
        }
        return goals;
    }

    /**
     * 查询当前有效的目标（生效日期区间包含今天）。
     */
    public List<Goal> findActive(int userId, LocalDate today) {
        String sql = SELECT_WITH_SUBJECT
                + "WHERE g.user_id = ? AND g.start_date <= ? AND g.end_date >= ? "
                + "ORDER BY CASE g.period_type WHEN 'DAILY' THEN 0 WHEN 'WEEKLY' THEN 1 ELSE 2 END, g.id";

        List<Goal> goals = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, today.format(AppConfig.DATE_FORMATTER));
            ps.setString(3, today.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    goals.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询生效目标失败: " + e.getMessage(), e);
        }
        return goals;
    }

    /**
     * 统计同一周期、同一科目下是否已存在目标（用于重复校验）。
     */
    public Goal findByPeriodAndSubject(int userId, GoalPeriod period, Integer subjectId) {
        String sql = SELECT_WITH_SUBJECT
                + "WHERE g.user_id = ? AND g.period_type = ? AND "
                + (subjectId == null ? "g.subject_id IS NULL" : "g.subject_id = ?")
                + " LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, period.name());
            if (subjectId != null) {
                ps.setInt(3, subjectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询目标失败: " + e.getMessage(), e);
        }
    }

    /**
     * 新增目标。
     *
     * @return 新目标的主键
     */
    public int insert(Goal goal) {
        String sql = "INSERT INTO goal (user_id, subject_id, period_type, target_minutes, "
                + "start_date, end_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, goal.getUserId());
            if (goal.getSubjectId() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, goal.getSubjectId());
            }
            ps.setString(3, goal.getPeriodType().name());
            ps.setInt(4, goal.getTargetMinutes());
            ps.setString(5, goal.getStartDate().format(AppConfig.DATE_FORMATTER));
            ps.setString(6, goal.getEndDate().format(AppConfig.DATE_FORMATTER));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    goal.setId(id);
                    return id;
                }
            }
            throw new IllegalStateException("新增目标后未能获取主键");
        } catch (SQLException e) {
            throw new IllegalStateException("新增学习目标失败: " + e.getMessage(), e);
        }
    }

    /** 更新目标（科目、周期、目标时长）。 */
    public void update(Goal goal) {
        String sql = "UPDATE goal SET subject_id = ?, period_type = ?, target_minutes = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (goal.getSubjectId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, goal.getSubjectId());
            }
            ps.setString(2, goal.getPeriodType().name());
            ps.setInt(3, goal.getTargetMinutes());
            ps.setInt(4, goal.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新学习目标失败: " + e.getMessage(), e);
        }
    }

    /** 删除目标。 */
    public void delete(int goalId) {
        String sql = "DELETE FROM goal WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, goalId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除学习目标失败: " + e.getMessage(), e);
        }
    }

    /** 将结果集当前行映射为目标对象。 */
    private Goal mapRow(ResultSet rs) throws SQLException {
        Goal goal = new Goal();
        goal.setId(rs.getInt("id"));
        goal.setUserId(rs.getInt("user_id"));

        int subjectId = rs.getInt("subject_id");
        goal.setSubjectId(rs.wasNull() ? null : subjectId);

        goal.setSubjectName(rs.getString("subject_name"));
        goal.setPeriodType(GoalPeriod.valueOf(rs.getString("period_type")));
        goal.setTargetMinutes(rs.getInt("target_minutes"));
        goal.setStartDate(LocalDate.parse(rs.getString("start_date"), AppConfig.DATE_FORMATTER));
        goal.setEndDate(LocalDate.parse(rs.getString("end_date"), AppConfig.DATE_FORMATTER));

        String createdAt = rs.getString("created_at");
        goal.setCreatedAt(createdAt == null || createdAt.isBlank()
                ? null : LocalDateTime.parse(createdAt, AppConfig.DATE_TIME_FORMATTER));
        return goal;
    }
}