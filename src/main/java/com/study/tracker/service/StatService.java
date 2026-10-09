package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.GoalDao;
import com.study.tracker.dao.StatDao;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.dao.TaskDao;
import com.study.tracker.model.Goal;
import com.study.tracker.model.TaskStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 效率统计业务。
 * <p>
 * 综合效率指数由五项加权得到：
 * <ul>
 *   <li>任务完成率 30%</li>
 *   <li>番茄钟完成率 25%</li>
 *   <li>目标达成率 20%</li>
 *   <li>专注度（由中断频率换算）15%</li>
 *   <li>出勤率（有学习记录的天数占比）10%</li>
 * </ul>
 * 缺少数据的单项按 {@link #NEUTRAL} 折算，避免因为"没有任务"就把总分压到很低。
 */
public class StatService {

    /** 计入"有效专注"的最短时长（分钟）。 */
    private static final int MIN_EFFECTIVE_MINUTES = 5;

    /** 中断频率的容忍上限（次/次），用于换算专注度。 */
    private static final double MAX_INTERRUPTION_PER_SESSION = 3.0;

    /** 缺少数据时的中性取值。 */
    private static final double NEUTRAL = 0.5;

    /** 时段划分：下标 0 为起始小时，下标 1 为结束小时（不含）。 */
    private static final int[][] SLOT_RANGES = {
            {0, 6}, {6, 9}, {9, 12}, {12, 14}, {14, 18}, {18, 21}, {21, 24}
    };

    /** 时段名称，与 {@link #SLOT_RANGES} 一一对应。 */
    private static final String[] SLOT_NAMES = {
            "凌晨 0-6 点", "早晨 6-9 点", "上午 9-12 点", "午间 12-14 点",
            "下午 14-18 点", "晚上 18-21 点", "深夜 21-24 点"
    };

    private final StatDao statDao = new StatDao();
    private final StudyRecordDao recordDao = new StudyRecordDao();
    private final TaskDao taskDao = new TaskDao();
    private final GoalDao goalDao = new GoalDao();

    /**
     * 效率指标集合。
     *
     * @param totalMinutes          区间内学习总分钟数
     * @param activeDays            有学习记录的天数
     * @param avgDailyMinutes       有学习记录当天的平均时长
     * @param avgSessionMinutes     平均单次专注时长
     * @param interruptionPerSession 平均每次专注的暂停次数
     * @param taskCompletionRate    任务完成率
     * @param pomodoroCompletionRate 番茄钟完成率
     * @param goalAchievementRate   目标达成率
     * @param streakDays            连续学习天数
     * @param efficiencyScore       综合效率指数（0-100）
     */
    public record Metrics(
            int totalMinutes,
            int activeDays,
            double avgDailyMinutes,
            double avgSessionMinutes,
            double interruptionPerSession,
            double taskCompletionRate,
            double pomodoroCompletionRate,
            double goalAchievementRate,
            int streakDays,
            int efficiencyScore) {
    }

    /** 单个时段的统计结果。 */
    public record SlotStat(String label, int minutes) {
    }

    /**
     * 计算指定区间的效率指标。
     *
     * @param from 起始日期
     * @param to   结束日期
     */
    public Metrics computeMetrics(LocalDate from, LocalDate to) {
        int userId = UserSession.currentUserId();

        int totalMinutes = recordDao.sumDuration(userId, from, to);
        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;

        Set<LocalDate> studyDates = new LinkedHashSet<>();
        for (LocalDate date : statDao.findStudyDates(userId)) {
            if (!date.isBefore(from) && !date.isAfter(to)) {
                studyDates.add(date);
            }
        }
        int activeDays = studyDates.size();
        double avgDaily = activeDays == 0 ? 0 : (double) totalMinutes / activeDays;

        int effectiveSessions = statDao.countRecords(userId, from, to, MIN_EFFECTIVE_MINUTES);
        double avgSession = effectiveSessions == 0 ? 0 : (double) totalMinutes / effectiveSessions;

        int allSessions = statDao.countRecords(userId, from, to);
        int pauseTotal = statDao.sumPauseCount(userId, from, to);
        double interruptionPerSession = allSessions == 0 ? 0 : (double) pauseTotal / allSessions;

        double taskRate = computeTaskCompletionRate(userId);
        double pomodoroRate = computePomodoroCompletionRate(userId, from, to);
        double goalRate = computeGoalAchievementRate();

        double focusScore = 1 - Math.min(interruptionPerSession / MAX_INTERRUPTION_PER_SESSION, 1);
        double attendance = totalDays <= 0 ? 0 : Math.min((double) activeDays / totalDays, 1);

        int score = (int) Math.round(100 * (0.3 * taskRate + 0.25 * pomodoroRate
                + 0.2 * goalRate + 0.15 * focusScore + 0.1 * attendance));

        return new Metrics(totalMinutes, activeDays, avgDaily, avgSession, interruptionPerSession,
                taskRate, pomodoroRate, goalRate, streakDays(), score);
    }

    /**
     * 按预设时段统计学习时长。
     *
     * @return 各时段统计，顺序与时段划分一致
     */
    public List<SlotStat> analyzeTimeSlots(LocalDate from, LocalDate to) {
        Map<Integer, Integer> byHour = statDao.sumMinutesByHour(UserSession.currentUserId(), from, to);
        List<SlotStat> result = new ArrayList<>();

        for (int i = 0; i < SLOT_RANGES.length; i++) {
            int startHour = SLOT_RANGES[i][0];
            int endHour = SLOT_RANGES[i][1];
            int minutes = 0;
            for (int hour = startHour; hour < endHour; hour++) {
                minutes += byHour.getOrDefault(hour, 0);
            }
            result.add(new SlotStat(SLOT_NAMES[i], minutes));
        }
        return result;
    }

    /**
     * 计算连续学习天数。
     * <p>
     * 若今天尚无记录，则从昨天开始回溯，避免"今天还没学"就把连续记录清零。
     */
    public int streakDays() {
        Set<LocalDate> dates = statDao.findStudyDates(UserSession.currentUserId());
        LocalDate cursor = LocalDate.now();
        if (!dates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }

        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 计划完成率 = 已完成任务 / 全部任务。 */
    private double computeTaskCompletionRate(int userId) {
        int done = taskDao.countByStatus(userId, TaskStatus.DONE);
        int todo = taskDao.countByStatus(userId, TaskStatus.TODO);
        int total = done + todo;
        return total == 0 ? NEUTRAL : (double) done / total;
    }

    /** 番茄钟完成率 = 完整完成的番茄数 / 番茄钟记录总数。 */
    private double computePomodoroCompletionRate(int userId, LocalDate from, LocalDate to) {
        int[] counts = statDao.countPomodoro(userId, from, to);
        return counts[0] == 0 ? NEUTRAL : (double) counts[1] / counts[0];
    }

    /** 目标达成率 = 各目标完成率的平均值（单项上限 100%）。 */
    private double computeGoalAchievementRate() {
        List<Goal> goals = goalDao.findAll(UserSession.currentUserId());
        if (goals.isEmpty()) {
            return NEUTRAL;
        }

        GoalService goalService = new GoalService();
        double sum = 0;
        for (Goal goal : goals) {
            GoalService.Progress progress = goalService.progressOf(goal);
            sum += Math.min((double) progress.percentRaw() / 100, 1);
        }
        return sum / goals.size();
    }
}
