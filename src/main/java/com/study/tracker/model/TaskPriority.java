package com.study.tracker.model;

/**
 * 待办任务的优先级。
 */
public enum TaskPriority {

    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    private final String displayName;

    TaskPriority(String displayName) {
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