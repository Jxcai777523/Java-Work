package com.study.tracker.model;

/**
 * 学习记录的来源。
 */
public enum RecordSource {

    /** 由计时器自动生成。 */
    AUTO("自动计时"),

    /** 用户手动补录。 */
    MANUAL("手动补录");

    private final String displayName;

    RecordSource(String displayName) {
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