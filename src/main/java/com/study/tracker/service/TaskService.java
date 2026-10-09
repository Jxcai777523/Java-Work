package com.study.tracker.service;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.TaskDao;
import com.study.tracker.model.Task;
import com.study.tracker.model.TaskPriority;
import com.study.tracker.model.TaskStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 待办任务业务。
 * <p>
 * 只负责任务本身的增删改查与状态切换，统计口径由
 * {@link StatService} 统一处理，避免两处各算一套。
 */
public class TaskService {

    private final TaskDao taskDao = new TaskDao();

    /**
     * 查询当前用户的任务。
     *
     * @param onlyTodo 是否只返回未完成任务
     */
    public List<Task> list(boolean onlyTodo) {
        return taskDao.findAll(UserSession.currentUserId(), onlyTodo);
    }

    /**
     * 新建任务。
     *
     * @throws IllegalArgumentException 标题为空或过长时抛出
     */
    public Task create(String title, Integer subjectId, TaskPriority priority,
                       LocalDate dueDate, int estimatedMinutes) {
        Task task = new Task();
        task.setUserId(UserSession.currentUserId());
        task.setTitle(validateTitle(title));
        task.setSubjectId(subjectId);
        task.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        task.setDueDate(dueDate);
        task.setEstimatedMinutes(Math.max(estimatedMinutes, 0));
        task.setStatus(TaskStatus.TODO);
        taskDao.insert(task);
        return task;
    }

    /**
     * 修改任务。
     *
     * @throws IllegalArgumentException 标题为空或过长时抛出
     */
    public void update(Task task, String title, Integer subjectId, TaskPriority priority,
                       LocalDate dueDate, int estimatedMinutes) {
        task.setTitle(validateTitle(title));
        task.setSubjectId(subjectId);
        task.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        task.setDueDate(dueDate);
        task.setEstimatedMinutes(Math.max(estimatedMinutes, 0));
        taskDao.update(task);
    }

    /** 切换任务状态。 */
    public void setStatus(Task task, TaskStatus status) {
        taskDao.updateStatus(task.getId(), status);
        task.setStatus(status);
    }

    /** 删除任务。 */
    public void delete(int taskId) {
        taskDao.delete(taskId);
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("任务标题不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("任务标题不能超过 100 个字符");
        }
        return trimmed;
    }
}
