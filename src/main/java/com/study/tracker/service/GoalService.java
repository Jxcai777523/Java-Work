package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.GoalDao;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.model.Goal;
import com.study.tracker.model.GoalPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 学习目标业务。
 * <p>
 * 负责目标的增删改查与进度计算。同一周期、同一科目只允许存在一个目标，
 * 进度按当前周期的实际学习时长与目标时长比较得出。
 */
public class GoalService {

    private final GoalDao goalDao = new GoalDao();
    private final StudyRecordDao recordDao = new StudyRecordDao();

    /**
     * 目标完成进度。
     *
     * @param goal            目标
     * @param achievedMinutes 当前周期已完成的分钟数
     */
    public record Progress(Goal goal, int achievedMinutes) {

        /** 原始完成百分比，可能超过 100。 */
        public int percentRaw() {
            if (goal.getTargetMinutes() <= 0) {
                return 0;
            }
            return (int) Math.round((double) achievedMinutes / goal.getTargetMinutes() * 100);
        }

        /** 用于界面展示的完成百分比，上限 100。 */
        public int percentCapped() {
            return Math.min(percentRaw(), 100);
        }
    }

    /** 查询全部目标。 */
    public List<Goal> list() {
        return goalDao.findAll(UserSession.currentUserId());
    }

    /**
     * 新增目标。
     *
     * @throws IllegalArgumentException 目标时长非法或重复时抛出
     */
    public Goal create(GoalPeriod period, Integer subjectId, int targetMinutes) {
        validate(period, targetMinutes);

        Goal existing = goalDao.findByPeriodAndSubject(UserSession.currentUserId(), period, subjectId);
        if (existing != null) {
            throw new IllegalArgumentException("同一周期、同一科目下已存在目标，请直接修改");
        }

        Goal goal = new Goal();
        goal.setUserId(UserSession.currentUserId());
        goal.setPeriodType(period);
        goal.setSubjectId(subjectId);
        goal.setTargetMinutes(targetMinutes);
        goal.setStartDate(LocalDate.now());
        // 目标视为长期有效，结束日期设定为远期
        goal.setEndDate(LocalDate.of(2099, 12, 31));
        goalDao.insert(goal);
        return goal;
    }

    /**
     * 修改目标。
     *
     * @throws IllegalArgumentException 目标时长非法或与他项重复时抛出
     */
    public void update(Goal goal, GoalPeriod period, Integer subjectId, int targetMinutes) {
        validate(period, targetMinutes);

        Goal existing = goalDao.findByPeriodAndSubject(UserSession.currentUserId(), period, subjectId);
        if (existing != null && existing.getId() != goal.getId()) {
            throw new IllegalArgumentException("同一周期、同一科目下已存在目标");
        }

        goal.setPeriodType(period);
        goal.setSubjectId(subjectId);
        goal.setTargetMinutes(targetMinutes);
        goalDao.update(goal);
    }

    /** 删除目标。 */
    public void delete(int goalId) {
        goalDao.delete(goalId);
    }

    /** 计算某个目标在当前周期的完成进度。 */
    public Progress progressOf(Goal goal) {
        LocalDate[] range = currentRange(goal.getPeriodType());
        int minutes = recordDao.sumDuration(UserSession.currentUserId(), range[0], range[1], goal.getSubjectId());
        return new Progress(goal, minutes);
    }

    /**
     * 返回指定周期在当前日期下的起止范围。
     *
     * @return 长度为 2 的数组：下标 0 为起始日期，下标 1 为结束日期
     */
    public LocalDate[] currentRange(GoalPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAILY -> new LocalDate[]{today, today};
            case WEEKLY -> new LocalDate[]{
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            };
            case MONTHLY -> new LocalDate[]{
                    today.withDayOfMonth(1),
                    today.with(TemporalAdjusters.lastDayOfMonth())
            };
        };
    }

    private void validate(GoalPeriod period, int targetMinutes) {
        if (period == null) {
            throw new IllegalArgumentException("请选择目标周期");
        }
        if (targetMinutes <= 0) {
            throw new IllegalArgumentException("目标时长必须大于 0");
        }
    }
}
