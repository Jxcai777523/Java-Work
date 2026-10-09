package com.study.tracker.service;

import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.Subject;
import com.study.tracker.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("计时器与番茄钟")
class TimerServiceTest {

    private final SubjectService subjectService = new SubjectService();

    @BeforeEach
    void setUp() {
        TestDatabase.resetWithAdmin("timer");
    }

    @Test
    @DisplayName("完成专注阶段按实际有效时长记录，暂停部分被扣除")
    void focusPhaseRecordsEffectiveMinutes() throws Exception {
        Subject subject = subjectService.create("番茄科目", "#123456", 60);
        TimerService timer = new TimerService();
        timer.startPomodoro(subject.getId(), 25, 5);

        Thread.sleep(1100);
        timer.pause();
        Thread.sleep(1100);
        timer.resume();

        StudyRecord record = timer.completeFocusPhase("番茄钟测试");

        assertTrue(record.isCompleted(), "自然完成的专注阶段应标记为完成");
        assertTrue(record.getPausedSeconds() >= 1, "暂停秒数应写入记录");
        assertEquals(1, record.getDurationMinutes(), "不足一分钟的有效学习按 1 分钟计");
        assertTrue(record.getDurationMinutes() < 25,
                "有效时长应来自实际经过时间，而不是标称的 25 分钟");
    }

    @Test
    @DisplayName("专注完成后进入休息阶段并重置暂停计数")
    void focusCompletionEntersBreakPhase() {
        Subject subject = subjectService.create("番茄科目B", "#654321", 60);
        TimerService timer = new TimerService();
        timer.startPomodoro(subject.getId(), 25, 5);

        timer.pause();
        timer.resume();
        timer.completeFocusPhase(null);

        assertEquals(TimerService.PomodoroPhase.BREAK, timer.getPhase());
        assertTrue(timer.isRunning(), "进入休息阶段后应继续计时");
        assertEquals(0, timer.getPauseCount(), "新阶段从零开始统计暂停");
        assertEquals(1, timer.getCompletedPomodoros());
    }
}
