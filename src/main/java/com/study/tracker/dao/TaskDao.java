package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.Task;
import com.study.tracker.model.TaskPriority;
import com.study.tracker.model.TaskStatus;

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
 * 待办任务数据访问对象。
 * <p>
 * 查询时通过 LEFT JOIN 带出科目名称；{@code subject_id} 允许为空，
 * 表示该任务不与具体科目关联。
 */
public class TaskDao {

    private static final String SELECT_WITH_SUBJECT =
            "SELECT t.id, t.user_id, t.subject_id, s.name AS subject_name, t.title, t.priority, "
          + "t.due_date, t.estimated_minutes, t.status, t.completed_at, t.created_at "
          + "FROM task t LEFT JOIN subject s ON t.subject_id = s.id ";

    /**
     * 查询任务列表。
     *
     * @param userId   用户 ID
     * @param onlyTodo 是否只返回未完成任务
     * @return 任务列表（未完成在前，按截止日期升序）
     */
    public List<Task> findAll(int userId, boolean onlyTodo) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_SUBJECT);
        sql.append("WHERE t.user_id = ? ");
        if (onlyTodo) {
            sql.append("AND t.status = 'TODO' ");
        }
        sql.append("ORDER BY CASE t.status WHEN 'TODO' THEN 0 ELSE 1 END, ");
        sql.append("CASE WHEN t.due_date IS NULL THEN 1 ELSE 0 END, t.due_date ASC, t.id DESC");

        List<Task> tasks = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询任务列表失败: " + e.getMessage(), e);
        }
        return tasks;
    }

    /**
     * 新增任务。
     *
     * @return 新任务的主键
     */
    public int insert(Task task) {
        String sql = "INSERT INTO task (user_id, subject_id, title, priority, due_date, "
                + "estimated_minutes, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, task.getUserId());
            setNullableInt(ps, 2, task.getSubjectId());
            ps.setString(3, task.getTitle());
            ps.setString(4, task.getPriority().name());
            ps.setString(5, task.getDueDate() == null ? null : task.getDueDate().format(AppConfig.DATE_FORMATTER));
            ps.setInt(6, task.getEstimatedMinutes());
            ps.setString(7, task.getStatus().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    task.setId(id);
                    return id;
                }
            }
            throw new IllegalStateException("新增任务后未能获取主键");
        } catch (SQLException e) {
            throw new IllegalStateException("新增任务失败: " + e.getMessage(), e);
        }
    }

    /** 更新任务内容（标题、科目、优先级、截止日期、预计时长）。 */
    public void update(Task task) {
        String sql = "UPDATE task SET subject_id = ?, title = ?, priority = ?, due_date = ?, "
                + "estimated_minutes = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, task.getSubjectId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getPriority().name());
            ps.setString(4, task.getDueDate() == null ? null : task.getDueDate().format(AppConfig.DATE_FORMATTER));
            ps.setInt(5, task.getEstimatedMinutes());
            ps.setInt(6, task.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新任务状态，并在完成时记录完成时间。
     * <p>
     * 改回未完成时保留原来的 {@code completed_at}：完成时间既不在界面上展示，
     * 也不参与统计，但误点"取消完成"不该把历史信息抹掉；再次标记完成时会写入新时间。
     */
    public void updateStatus(int taskId, TaskStatus status) {
        String sql = "UPDATE task SET status = ?, completed_at = COALESCE(?, completed_at) WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, status == TaskStatus.DONE
                    ? LocalDateTime.now().format(AppConfig.DATE_TIME_FORMATTER) : null);
            ps.setInt(3, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新任务状态失败: " + e.getMessage(), e);
        }
    }

    /** 删除任务。 */
    public void delete(int taskId) {
        String sql = "DELETE FROM task WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除任务失败: " + e.getMessage(), e);
        }
    }

    /** 统计指定状态的任务数量。 */
    public int countByStatus(int userId, TaskStatus status) {
        String sql = "SELECT COUNT(*) FROM task WHERE user_id = ? AND status = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计任务数量失败: " + e.getMessage(), e);
        }
    }

    /** 统计截止日期已到（含今天）的未完成任务数量。 */
    public int countOverdue(int userId, LocalDate today) {
        String sql = "SELECT COUNT(*) FROM task WHERE user_id = ? AND status = 'TODO' "
                + "AND due_date IS NOT NULL AND due_date <= ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, today.format(AppConfig.DATE_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计逾期任务失败: " + e.getMessage(), e);
        }
    }

    /** 将结果集当前行映射为任务对象。 */
    private Task mapRow(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getInt("id"));
        task.setUserId(rs.getInt("user_id"));

        int subjectId = rs.getInt("subject_id");
        task.setSubjectId(rs.wasNull() ? null : subjectId);

        task.setSubjectName(rs.getString("subject_name"));
        task.setTitle(rs.getString("title"));
        task.setPriority(TaskPriority.valueOf(rs.getString("priority")));

        String dueDate = rs.getString("due_date");
        task.setDueDate(dueDate == null || dueDate.isBlank()
                ? null : LocalDate.parse(dueDate, AppConfig.DATE_FORMATTER));

        task.setEstimatedMinutes(rs.getInt("estimated_minutes"));
        task.setStatus(TaskStatus.valueOf(rs.getString("status")));

        String completedAt = rs.getString("completed_at");
        task.setCompletedAt(completedAt == null || completedAt.isBlank()
                ? null : LocalDateTime.parse(completedAt, AppConfig.DATE_TIME_FORMATTER));

        String createdAt = rs.getString("created_at");
        task.setCreatedAt(createdAt == null || createdAt.isBlank()
                ? null : LocalDateTime.parse(createdAt, AppConfig.DATE_TIME_FORMATTER));

        return task;
    }

    /** 设置可空的整型参数。 */
    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}