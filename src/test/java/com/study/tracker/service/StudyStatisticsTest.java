package com.study.tracker.service;

import com.study.tracker.dao.DBUtil;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.Subject;
import com.study.tracker.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("学习记录与效率统计")
class StudyStatisticsTest {

    private final RecordService recordService = new RecordService();
    private final StatService statService = new StatService();

    @BeforeEach
    void setUp() {
        TestDatabase.resetWithAdmin("stats");
    }

    @Test
    @DisplayName("补录的记录会计入当天总时长与连续学习天数")
    void manualRecordCountsInStatistics() {
        Subject subject = new SubjectService().create("科目A", "#111111", 60);
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atTime(9, 0);

        recordService.addManual(subject.getId(), start, start.plusMinutes(45), null);

        StatService.Metrics metrics = statService.computeMetrics(today, today);
        assertEquals(45, metrics.totalMinutes());
        assertEquals(1, recordService.search(today, today, null).size());
        assertTrue(statService.streakDays() >= 1);
    }

    @Test
    @DisplayName("结束时间早于开始时间会被拒绝")
    void invalidTimeRangeIsRejected() {
        Subject subject = new SubjectService().create("科目B", "#222222", 60);
        LocalDateTime start = LocalDate.now().atTime(10, 0);

        assertThrows(IllegalArgumentException.class,
                () -> recordService.addManual(subject.getId(), start, start.minusMinutes(10), null));
    }

    @Test
    @DisplayName("删除记录后统计同步减少")
    void deletingRecordUpdatesStatistics() {
        Subject subject = new SubjectService().create("科目C", "#333333", 60);
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atTime(14, 0);

        var record = recordService.addManual(subject.getId(), start, start.plusMinutes(30), null);
        assertEquals(30, statService.computeMetrics(today, today).totalMinutes());

        recordService.delete(record.getId());
        assertEquals(0, statService.computeMetrics(today, today).totalMinutes());
    }

    @Test
    @DisplayName("编辑记录时按有效时长重算，原有暂停被扣除")
    void editingRecordExcludesExistingPause() throws Exception {
        Subject subject = new SubjectService().create("科目D", "#444444", 60);
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atTime(9, 0);
        StudyRecord record = recordService.addManual(subject.getId(), start, start.plusMinutes(30), null);

        // 直接改库，把这条记录模拟成"含 10 分钟暂停的自动计时记录"
        try (Connection conn = DBUtil.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE study_record SET paused_seconds = 600, pause_count = 3, "
                    + "source = 'AUTO' WHERE id = " + record.getId());
        }
        record.setPausedSeconds(600);
        record.setPauseCount(3);

        recordService.update(record, subject.getId(), start, start.plusMinutes(30), "编辑后");

        StudyRecord reloaded = recordService.search(today, today, null).get(0);
        assertEquals(20, reloaded.getDurationMinutes(), "30 分钟区间扣除 10 分钟暂停后应为 20 分钟");
        assertEquals(600, reloaded.getPausedSeconds(), "编辑不应破坏原有暂停数据");
        assertEquals(3, reloaded.getPauseCount());
    }
}
