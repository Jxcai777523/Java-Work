package com.study.tracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * 偏好设置数据访问对象。
 * <p>
 * 设置项按"键 → 值"存放在 preferences 表，全部以字符串形式保存，
 * 由调用方负责解析与校验。表结构见 {@code db/schema.sql}。
 */
public class PreferenceDao {

    /**
     * 读取全部设置项。
     *
     * @return 键 → 值；没有任何记录时返回空 Map
     */
    public Map<String, String> findAll() {
        Map<String, String> result = new HashMap<>();
        String sql = "SELECT pref_key, pref_value FROM preferences";

        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("pref_key"), rs.getString("pref_value"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取偏好设置失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 保存单个设置项，已存在则覆盖。
     *
     * @param key   设置项名称
     * @param value 设置项取值
     */
    public void save(String key, String value) {
        String sql = "INSERT INTO preferences (pref_key, pref_value, updated_at) "
                + "VALUES (?, ?, datetime('now', 'localtime')) "
                + "ON CONFLICT(pref_key) DO UPDATE SET "
                + "pref_value = excluded.pref_value, updated_at = excluded.updated_at";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存偏好设置失败: " + e.getMessage(), e);
        }
    }
}
