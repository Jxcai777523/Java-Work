package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.model.RecordSource;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.TimerMode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计时业务。
 * <p>
 * 同时支持两种模式：
 * <ul>
 *   <li><b>自由计时</b>：开始 → 暂停/继续 → 结束，结束时写入一条记录；</li>
 *   <li><b>番茄钟</b>：专注与休息阶段交替，专注阶段结束后自动写入一条记录并进入休息，
 *       用户中途放弃时，只要已经学了一段时间也会保存下来。</li>
 * </ul>
 * 所有时长都会扣除暂停时间，不足一分钟但确有学习时按一分钟计。
 */
public class TimerService {

    /** 默认专注时长（分钟）。 */
    public static final int DEFAULT_FOCUS_MINUTES = 25;

    /** 默认休息时长（分钟）。 */
    public static final int DEFAULT_BREAK_MINUTES = 5;

    private final StudyRecordDao recordDao = new StudyRecordDao();

    private State state = State.IDLE;
    private int subjectId;
    private LocalDateTime startTime;
    private LocalDateTime pauseStartTime;
    private long pausedSeconds;
    private int pauseCount;
    private TimerMode mode = TimerMode.FREE;
    private PomodoroPhase phase = PomodoroPhase.NONE;
    private LocalDateTime phaseEndTime;
    private int focusMinutes = DEFAULT_FOCUS_MINUTES;
    private int breakMinutes = DEFAULT_BREAK_MINUTES;
    private int completedPomodoros;

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public TimerMode getMode() {
        return mode;
    }

    public PomodoroPhase getPhase() {
        return phase;
    }

    public int getFocusMinutes() {
        return focusMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setFocusMinutes(int focusMinutes) {
        this.focusMinutes = Math.max(1, focusMinutes);
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = Math.max(1, breakMinutes);
    }

    public int getCompletedPomodoros() {
        return completedPomodoros;
    }

    public int getPauseCount() {
        return pauseCount;
    }

    /**
     * 开始一次自由计时。
     *
     * @throws IllegalStateException 已有计时在进行时抛出
     */
    public void start(int subjectId, TimerMode mode) {
        if (state != State.IDLE) {
            throw new IllegalStateException("已有计时正在进行，请先结束当前计时");
        }

        this.subjectId = subjectId;
        this.mode = mode;
        this.startTime = LocalDateTime.now();
        this.pausedSeconds = 0;
        this.pauseCount = 0;
        this.pauseStartTime = null;
        this.phase = PomodoroPhase.NONE;
        this.phaseEndTime = null;
        this.state = State.RUNNING;
    }

    /** 开始一次番茄钟，默认从专注阶段起步。 */
    public void startPomodoro(int subjectId, int focusMinutes, int breakMinutes) {
        setFocusMinutes(focusMinutes);
        setBreakMinutes(breakMinutes);
        start(subjectId, TimerMode.POMODORO);

        completedPomodoros = 0;
        phase = PomodoroPhase.FOCUS;
        phaseEndTime = startTime.plusMinutes(this.focusMinutes);
    }

    /**
     * 暂停计时。
     *
     * @throws IllegalStateException 当前不在计时中时抛出
     */
    public void pause() {
        if (state != State.RUNNING) {
            throw new IllegalStateException("当前不在计时状态，无法暂停");
        }
        pauseStartTime = LocalDateTime.now();
        pauseCount++;
        state = State.PAUSED;
    }

    /**
     * 继续计时。
     * <p>
     * 会把本次暂停的时长计入总暂停，并顺延当前阶段的结束时刻，
     * 保证"暂停多久，阶段就推迟多久"。
     *
     * @throws IllegalStateException 当前不处于暂停状态时抛出
     */
    public void resume() {
        if (state != State.PAUSED) {
            throw new IllegalStateException("当前不处于暂停状态");
        }

        LocalDateTime now = LocalDateTime.now();
        if (pauseStartTime != null) {
            long paused = Duration.between(pauseStartTime, now).getSeconds();
            pausedSeconds += paused;
            if (phaseEndTime != null) {
                phaseEndTime = phaseEndTime.plusSeconds(paused);
            }
            pauseStartTime = null;
        }
        state = State.RUNNING;
    }

    /**
     * 结束计时并保存记录。
     *
     * @return 已保存的学习记录
     * @throws IllegalStateException 尚未开始计时时抛出
     */
    public StudyRecord finish(String remark) {
        if (state == State.IDLE) {
            throw new IllegalStateException("尚未开始计时");
        }

        LocalDateTime endTime = LocalDateTime.now();
        if (state == State.PAUSED) {
            settlePause(endTime);
        }

        StudyRecord record = buildRecord(startTime, endTime, toMinutes(effectiveSeconds(endTime)), remark, true);
        recordDao.insert(record);
        reset();
        return record;
    }

    /**
     * 完成当前专注阶段：保存这条记录并进入休息阶段。
     *
     * @throws IllegalStateException 当前不在专注阶段时抛出
     */
    public StudyRecord completeFocusPhase(String remark) {
        if (phase != PomodoroPhase.FOCUS) {
            throw new IllegalStateException("当前不在专注阶段");
        }

        LocalDateTime endTime = LocalDateTime.now();
        if (state == State.PAUSED) {
            settlePause(endTime);
        }

        // 时长与 finish()、abandonPomodoro() 采用同一口径：由实际有效秒数换算，
        // 而不是写死标称的 focusMinutes，否则记录里的"有效时长"会与
        // "起止时间差 − 暂停秒数"对不上。正常按时完成时结果就等于 focusMinutes。
        StudyRecord record = buildRecord(startTime, endTime, toMinutes(effectiveSeconds(endTime)), remark, true);
        recordDao.insert(record);
        completedPomodoros++;

        phase = PomodoroPhase.BREAK;
        phaseEndTime = endTime.plusMinutes(breakMinutes);
        startTime = endTime;
        pausedSeconds = 0;
        pauseCount = 0;
        pauseStartTime = null;
        state = State.RUNNING;
        return record;
    }

    /**
     * 休息结束，进入下一个专注阶段。
     *
     * @throws IllegalStateException 当前不在休息阶段时抛出
     */
    public void completeBreakPhase() {
        if (phase != PomodoroPhase.BREAK) {
            throw new IllegalStateException("当前不在休息阶段");
        }

        startTime = LocalDateTime.now();
        phaseEndTime = startTime.plusMinutes(focusMinutes);
        pausedSeconds = 0;
        pauseCount = 0;
        pauseStartTime = null;
        state = State.RUNNING;
    }

    /**
     * 放弃番茄钟。
     * <p>
     * 处于专注阶段且已经学过一段时间时，会把这段学习保存成未完成的记录，
     * 否则直接复位并返回 {@code null}。
     */
    public StudyRecord abandonPomodoro(String remark) {
        if (mode != TimerMode.POMODORO || phase == PomodoroPhase.NONE) {
            reset();
            return null;
        }

        StudyRecord saved = null;
        if (phase == PomodoroPhase.FOCUS) {
            LocalDateTime endTime = LocalDateTime.now();
            if (state == State.PAUSED) {
                settlePause(endTime);
            }

            long effective = effectiveSeconds(endTime);
            if (effective > 0) {
                saved = buildRecord(startTime, endTime, toMinutes(effective), remark, false);
                recordDao.insert(saved);
            }
        }

        reset();
        return saved;
    }

    /** 本次计时已经过的有效秒数（已扣除暂停）。 */
    public long getElapsedSeconds() {
        if (state == State.IDLE || startTime == null) {
            return 0;
        }

        long total = Duration.between(startTime, LocalDateTime.now()).getSeconds();
        long paused = pausedSeconds;
        if (state == State.PAUSED && pauseStartTime != null) {
            paused += Duration.between(pauseStartTime, LocalDateTime.now()).getSeconds();
        }
        return Math.max(total - paused, 0);
    }

    /** 当前阶段剩余秒数；非番茄钟阶段返回 0。 */
    public long getRemainingSeconds() {
        if (phaseEndTime == null) {
            return 0;
        }
        return Math.max(Duration.between(LocalDateTime.now(), phaseEndTime).getSeconds(), 0);
    }

    /** 当前阶段是否已经到时。 */
    public boolean isPhaseFinished() {
        return phase != PomodoroPhase.NONE && phaseEndTime != null && getRemainingSeconds() <= 0;
    }

    /** 今日累计学习分钟数。 */
    public int getTodayMinutes() {
        LocalDate today = LocalDate.now();
        return recordDao.sumDuration(UserSession.currentUserId(), today, today);
    }

    /** 组装一条待写入的学习记录。 */
    private StudyRecord buildRecord(LocalDateTime start, LocalDateTime end, int durationMinutes,
                                    String remark, boolean completed) {
        StudyRecord record = new StudyRecord();
        record.setUserId(UserSession.currentUserId());
        record.setSubjectId(subjectId);
        record.setStartTime(start);
        record.setEndTime(end);
        record.setDurationMinutes(durationMinutes);
        record.setPausedSeconds((int) Math.min(pausedSeconds, Integer.MAX_VALUE));
        record.setPauseCount(pauseCount);
        record.setMode(mode);
        record.setCompleted(completed);
        record.setSource(RecordSource.AUTO);
        record.setRemark(remark);
        return record;
    }

    /** 把秒数换算为分钟；不足一分钟但确有学习时，按 1 分钟计。 */
    private int toMinutes(long seconds) {
        int minutes = (int) Math.round(seconds / 60.0);
        if (minutes == 0 && seconds > 0) {
            return 1;
        }
        return minutes;
    }

    /** 结算当前这段暂停时间。 */
    private void settlePause(LocalDateTime endTime) {
        if (pauseStartTime != null) {
            pausedSeconds += Duration.between(pauseStartTime, endTime).getSeconds();
            pauseStartTime = null;
        }
    }

    /**
     * 从本次计时开始到 {@code endTime} 为止的有效秒数（已扣除累计暂停）。
     * <p>
     * 调用前应先用 {@link #settlePause(LocalDateTime)} 结算在途暂停，
     * 否则正在进行的那段暂停不会被扣除。
     */
    private long effectiveSeconds(LocalDateTime endTime) {
        long totalSeconds = Duration.between(startTime, endTime).getSeconds();
        return Math.max(totalSeconds - pausedSeconds, 0);
    }

    /** 复位状态机。 */
    private void reset() {
        state = State.IDLE;
        subjectId = 0;
        startTime = null;
        pauseStartTime = null;
        pausedSeconds = 0;
        pauseCount = 0;
        phase = PomodoroPhase.NONE;
        phaseEndTime = null;
        completedPomodoros = 0;
    }

    /** 计时状态。 */
    public enum State {

        /** 空闲。 */
        IDLE,

        /** 计时中。 */
        RUNNING,

        /** 已暂停。 */
        PAUSED
    }

    /** 番茄钟阶段。 */
    public enum PomodoroPhase {

        /** 非番茄钟。 */
        NONE("自由计时"),

        /** 专注中。 */
        FOCUS("专注中"),

        /** 休息中。 */
        BREAK("休息中");

        private final String displayName;

        PomodoroPhase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
