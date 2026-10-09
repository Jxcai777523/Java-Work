package com.study.tracker.service;

import com.study.tracker.model.Task;
import com.study.tracker.model.TaskPriority;
import com.study.tracker.model.TaskStatus;
import com.study.tracker.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("待办任务业务")
class TaskServiceTest {

    private final TaskService taskService = new TaskService();

    @BeforeEach
    void setUp() {
        TestDatabase.resetWithAdmin("task");
    }

    @Test
    @DisplayName("取消完成不会清空原完成时间，再次完成会刷新完成时间")
    void revertingCompletionKeepsCompletedAt() throws Exception {
        Task task = taskService.create("写课程报告", null, TaskPriority.MEDIUM, LocalDate.now(), 120);

        taskService.setStatus(task, TaskStatus.DONE);
        LocalDateTime first = reload(task.getId()).getCompletedAt();
        assertNotNull(first, "标记完成后应写入完成时间");

        Thread.sleep(1100);
        taskService.setStatus(task, TaskStatus.TODO);
        assertEquals(first, reload(task.getId()).getCompletedAt(), "取消完成不应丢失原完成时间");

        taskService.setStatus(task, TaskStatus.DONE);
        assertTrue(reload(task.getId()).getCompletedAt().isAfter(first), "再次完成应刷新完成时间");
    }

    @Test
    @DisplayName("任务状态可以在未完成与已完成之间切换")
    void statusCanBeToggled() {
        Task task = taskService.create("复习数据结构", null, TaskPriority.HIGH, LocalDate.now().plusDays(1), 60);

        assertEquals(TaskStatus.TODO, reload(task.getId()).getStatus());
        taskService.setStatus(task, TaskStatus.DONE);
        assertEquals(TaskStatus.DONE, reload(task.getId()).getStatus());
    }

    /** 从数据库重新读取任务，避免直接相信内存中的对象。 */
    private Task reload(int id) {
        return taskService.list(false).stream()
                .filter(item -> item.getId() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("任务不存在: " + id));
    }
}
