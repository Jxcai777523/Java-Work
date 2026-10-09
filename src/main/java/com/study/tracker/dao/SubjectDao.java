package com.study.tracker.dao;

import com.study.tracker.config.AppConfig;
import com.study.tracker.model.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 科目数据访问对象。
 * <p>
 * 删除采用"归档"（逻辑删除）方式，保证历史学习记录所引用的科目信息不会丢失；
 * 仅当科目下没有任何学习记录时，才允许物理删除。
 */
public class SubjectDao {

    private static final String COLUMNS =
            "id, name, color, weekly_target_minutes, archived, created_at";

    /**
     * 查询科目列表。
     *
     * @param includeArchived 是否包含已归档科目
     * @return 科目列表
     */
    public List<Subject> findAll(boolean includeArchived) {
        String sql = "SELECT " + COLUMNS + " FROM subject "
                + (includeArchived ? "" : "WHERE archived = 0 ")
                + "ORDER BY archived ASC, id ASC";

        List<Subject> subjects = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                subjects.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询科目列表失败: " + e.getMessage(), e);
        }
        return subjects;
    }

    /**
     * 按主键查询科目。
     *
     * @return 找到则返回科目对象，否则返回 {@code null}
     */
    public Subject findById(int id) {
        String sql = "SELECT " + COLUMNS + " FROM subject WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询科目失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按名称精确查询科目，用于重名校验。
     *
     * @return 找到则返回科目对象，否则返回 {@code null}
     */
    public Subject findByName(String name) {
        String sql = "SELECT " + COLUMNS + " FROM subject WHERE name = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("按名称查询科目失败: " + e.getMessage(), e);
        }
    }

    /**
     * 新增科目。
     *
     * @return 新科目的主键
     */
    public int insert(Subject subject) {
        String sql = "INSERT INTO subject (name, color, weekly_target_minutes, archived) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, subject.getName());
            ps.setString(2, subject.getColor());
            ps.setInt(3, subject.getWeeklyTargetMinutes());
            ps.setInt(4, subject.isArchived() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    subject.setId(id);
                    return id;
                }
            }
            throw new IllegalStateException("新增科目后未能获取主键");
        } catch (SQLException e) {
            throw new IllegalStateException("新增科目失败: " + e.getMessage(), e);
        }
    }

    /** 更新科目信息（名称、颜色、周目标）。 */
    public void update(Subject subject) {
        String sql = "UPDATE subject SET name = ?, color = ?, weekly_target_minutes = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subject.getName());
            ps.setString(2, subject.getColor());
            ps.setInt(3, subject.getWeeklyTargetMinutes());
            ps.setInt(4, subject.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新科目失败: " + e.getMessage(), e);
        }
    }

    /** 设置科目的归档状态。 */
    public void updateArchived(int id, boolean archived) {
        String sql = "UPDATE subject SET archived = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, archived ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新科目归档状态失败: " + e.getMessage(), e);
        }
    }

    /** 物理删除科目。 */
    public void delete(int id) {
        String sql = "DELETE FROM subject WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除科目失败: " + e.getMessage(), e);
        }
    }

    /** 统计指定科目下关联的学习记录条数。 */
    public int countRecords(int subjectId) {
        String sql = "SELECT COUNT(*) FROM study_record WHERE subject_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计科目记录数失败: " + e.getMessage(), e);
        }
    }

    /** 将结果集当前行映射为科目对象。 */
    private Subject mapRow(ResultSet rs) throws SQLException {
        Subject subject = new Subject();
        subject.setId(rs.getInt("id"));
        subject.setName(rs.getString("name"));
        subject.setColor(rs.getString("color"));
        subject.setWeeklyTargetMinutes(rs.getInt("weekly_target_minutes"));
        subject.setArchived(rs.getInt("archived") == 1);

        String createdAt = rs.getString("created_at");
        if (createdAt != null && !createdAt.isBlank()) {
            subject.setCreatedAt(LocalDateTime.parse(createdAt, AppConfig.DATE_TIME_FORMATTER));
        }
        return subject;
    }
}