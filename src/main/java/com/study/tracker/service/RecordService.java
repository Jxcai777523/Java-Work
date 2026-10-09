package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.StudyRecordDao;
import com.study.tracker.model.RecordSource;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.TimerMode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习记录业务。
 * <p>
 * 负责查询、修改、删除与手动补录学习记录；由计时器自动产生的记录
 * 由 {@link TimerService} 直接写入。
 */
public class RecordService {

    private final StudyRecordDao recordDao = new StudyRecordDao();

    /**
     * 按条件查询学习记录。
     *
     * @param from      起始日期，可为 {@code null}
     * @param to        结束日期，可为 {@code null}
     * @param subjectId 科目 ID，可为 {@code null} 表示不限
     */
    public List<StudyRecord> search(LocalDate from, LocalDate to, Integer subjectId) {
        return recordDao.search(UserSession.currentUserId(), from, to, subjectId);
    }

    /**
     * 修改已有学习记录。
     * <p>
     * 时长按建表脚本的口径重新计算：{@code duration_minutes} 是"有效时长"，
     * 需要扣除该记录原有的累计暂停，因此自动计时留下的暂停数据在编辑后仍然成立。
     *
     * @throws IllegalArgumentException 时间不合法时抛出
     */
    public void update(StudyRecord record, int subjectId, LocalDateTime startTime,
                       LocalDateTime endTime, String remark) {
        validateTimeRange(startTime, endTime);

        record.setSubjectId(subjectId);
        record.setStartTime(startTime);
        record.setEndTime(endTime);
        record.setDurationMinutes(effectiveMinutes(startTime, endTime, record.getPausedSeconds()));
        record.setRemark(remark);
        recordDao.update(record);
    }

    /** 删除学习记录。 */
    public void delete(int recordId) {
        recordDao.delete(recordId);
    }

    /**
     * 手动补录一条学习记录。
     *
     * @return 已保存的记录
     * @throws IllegalArgumentException 时间不合法时抛出
     */
    public StudyRecord addManual(int subjectId, LocalDateTime startTime,
                                 LocalDateTime endTime, String remark) {
        validateTimeRange(startTime, endTime);

        StudyRecord record = new StudyRecord();
        record.setUserId(UserSession.currentUserId());
        record.setSubjectId(subjectId);
        record.setStartTime(startTime);
        record.setEndTime(endTime);
        record.setDurationMinutes(toMinutes(startTime, endTime));
        record.setPausedSeconds(0);
        record.setPauseCount(0);
        record.setMode(TimerMode.FREE);
        record.setCompleted(true);
        record.setSource(RecordSource.MANUAL);
        record.setRemark(remark);
        recordDao.insert(record);
        return record;
    }

    /** 校验起止时间。 */
    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("开始时间与结束时间都不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
    }

    /** 由起止时间换算时长，不足一分钟按一分钟计。 */
    private int toMinutes(LocalDateTime startTime, LocalDateTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return (int) Math.max(minutes, 1);
    }

    /** 由起止时间扣除累计暂停后换算有效时长，至少 1 分钟。 */
    private int effectiveMinutes(LocalDateTime startTime, LocalDateTime endTime, int pausedSeconds) {
        long seconds = Duration.between(startTime, endTime).getSeconds() - Math.max(pausedSeconds, 0);
        return (int) Math.max(seconds / 60, 1);
    }
}
