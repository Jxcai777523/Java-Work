package com.study.tracker.model;

/**
 * 待办任务的状态。
 */
public enum TaskStatus {

    /** 未完成。 */
    TODO("待办"),

    /** 已完成。 */
    DONE("已完成");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 返回用于界面显示的中文名称。 */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}