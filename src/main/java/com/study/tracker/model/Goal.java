package com.study.tracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标。
 * <p>
 * 对应数据库表 {@code goal}。{@code subjectId} 为空表示"总目标"，
 * 否则表示针对某一科目的目标。目标达成率是效率指标体系的组成部分。
 */
public class Goal {

    private int id;
    private int userId;

    /** 关联科目，为空表示总目标。 */
    private Integer subjectId;

    /** 科目名称：由联表查询填充，仅用于界面展示。 */
    private String subjectName;

    private GoalPeriod periodType = GoalPeriod.DAILY;
    private int targetMinutes;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    public Goal() {
    }

    public Goal(GoalPeriod periodType, int targetMinutes, LocalDate startDate, LocalDate endDate) {
        this.periodType = periodType;
        this.targetMinutes = targetMinutes;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public GoalPeriod getPeriodType() {
        return periodType;
    }

    public void setPeriodType(GoalPeriod periodType) {
        this.periodType = periodType;
    }

    public int getTargetMinutes() {
        return targetMinutes;
    }

    public void setTargetMinutes(int targetMinutes) {
        this.targetMinutes = targetMinutes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}