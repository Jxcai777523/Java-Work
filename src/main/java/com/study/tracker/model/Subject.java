package com.study.tracker.model;

import java.time.LocalDateTime;

/**
 * 科目。
 * <p>
 * 对应数据库表 {@code subject}。科目是学习记录的分类维度，
 * 其颜色同时用于界面标记与统计图配色，保证同一科目在各处颜色一致。
 */
public class Subject {

    private int id;
    private String name;
    private String color;
    private int weeklyTargetMinutes;
    private boolean archived;
    private LocalDateTime createdAt;

    public Subject() {
    }

    public Subject(String name, String color, int weeklyTargetMinutes) {
        this.name = name;
        this.color = color;
        this.weeklyTargetMinutes = weeklyTargetMinutes;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getWeeklyTargetMinutes() {
        return weeklyTargetMinutes;
    }

    public void setWeeklyTargetMinutes(int weeklyTargetMinutes) {
        this.weeklyTargetMinutes = weeklyTargetMinutes;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 返回科目名称，便于在下拉框中直接显示。 */
    @Override
    public String toString() {
        return name;
    }
}