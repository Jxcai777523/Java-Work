package com.study.tracker.model;

import java.time.LocalDateTime;

/**
 * 学习记录。
 * <p>
 * 对应数据库表 {@code study_record}，是系统最核心的数据。
 * 一次"开始计时 → 结束计时"产生一条记录，其中：
 * <ul>
 *   <li>{@code pausedSeconds} 为累计暂停秒数，用于计算有效学习时长；</li>
 *   <li>{@code pauseCount} 为暂停次数，是"中断频率"效率指标的原始数据；</li>
 *   <li>{@code completed} 标记番茄钟是否完整完成，用于计算番茄钟完成率。</li>
 * </ul>
 */
public class StudyRecord {

    private int id;
    private int userId;
    private int subjectId;

    /** 科目名称：由联表查询填充，仅用于界面展示，不对应数据库字段。 */
    private String subjectName;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private int pausedSeconds;
    private int pauseCount;
    private TimerMode mode = TimerMode.FREE;
    private boolean completed = true;
    private RecordSource source = RecordSource.AUTO;
    private String remark;
    private LocalDateTime createdAt;

    public StudyRecord() {
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

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getPausedSeconds() {
        return pausedSeconds;
    }

    public void setPausedSeconds(int pausedSeconds) {
        this.pausedSeconds = pausedSeconds;
    }

    public int getPauseCount() {
        return pauseCount;
    }

    public void setPauseCount(int pauseCount) {
        this.pauseCount = pauseCount;
    }

    public TimerMode getMode() {
        return mode;
    }

    public void setMode(TimerMode mode) {
        this.mode = mode;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public RecordSource getSource() {
        return source;
    }

    public void setSource(RecordSource source) {
        this.source = source;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}