package com.study.tracker.model;

/**
 * 计时模式。
 */
public enum TimerMode {

    /** 自由计时：手动开始与结束。 */
    FREE("自由计时"),

    /** 番茄钟：专注与休息交替进行。 */
    POMODORO("番茄钟");

    private final String displayName;

    TimerMode(String displayName) {
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