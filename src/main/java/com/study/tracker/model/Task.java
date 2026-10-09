package com.study.tracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办任务。
 * <p>
 * 对应数据库表 {@code task}。任务的完成情况是"计划完成率"效率指标的原始数据。
 */
public class Task {

    private int id;
    private int userId;

    /** 所属科目，可为 {@code null}（表示不属于任何科目）。 */
    private Integer subjectId;

    /** 科目名称：由联表查询填充，仅用于界面展示。 */
    private String subjectName;

    private String title;
    private TaskPriority priority = TaskPriority.MEDIUM;
    private LocalDate dueDate;
    private int estimatedMinutes;
    private TaskStatus status = TaskStatus.TODO;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public Task() {
    }

    public Task(String title, Integer subjectId, TaskPriority priority, LocalDate dueDate) {
        this.title = title;
        this.subjectId = subjectId;
        this.priority = priority;
        this.dueDate = dueDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Integer getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}