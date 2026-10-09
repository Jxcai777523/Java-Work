package com.study.tracker.model;

/**
 * 学习目标的周期类型。
 */
public enum GoalPeriod {

    DAILY("每日"),
    WEEKLY("每周"),
    MONTHLY("每月");

    private final String displayName;

    GoalPeriod(String displayName) {
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