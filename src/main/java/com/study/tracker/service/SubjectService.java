package com.study.tracker.service;

import com.study.tracker.dao.SubjectDao;
import com.study.tracker.model.Subject;

import java.util.List;

/**
 * 科目业务逻辑。
 * <p>
 * 负责科目名称校验、重名校验，以及"有记录则不允许物理删除"的业务规则。
 */
public class SubjectService {

    private final SubjectDao subjectDao = new SubjectDao();

    /**
     * 查询科目列表。
     *
     * @param includeArchived 是否包含已归档科目
     */
    public List<Subject> list(boolean includeArchived) {
        return subjectDao.findAll(includeArchived);
    }

    /**
     * 新增科目。
     *
     * @throws IllegalArgumentException 名称为空或与现有科目重名时抛出
     */
    public Subject create(String name, String color, int weeklyTargetMinutes) {
        String trimmed = validateName(name);
        ensureNameAvailable(trimmed, null);

        Subject subject = new Subject(trimmed, color, Math.max(weeklyTargetMinutes, 0));
        subjectDao.insert(subject);
        return subject;
    }

    /**
     * 修改科目。
     *
     * @throws IllegalArgumentException 名称为空或与其他科目重名时抛出
     */
    public void update(Subject subject, String name, String color, int weeklyTargetMinutes) {
        String trimmed = validateName(name);
        ensureNameAvailable(trimmed, subject.getId());

        subject.setName(trimmed);
        subject.setColor(color);
        subject.setWeeklyTargetMinutes(Math.max(weeklyTargetMinutes, 0));
        subjectDao.update(subject);
    }

    /** 归档或恢复科目。 */
    public void setArchived(int subjectId, boolean archived) {
        subjectDao.updateArchived(subjectId, archived);
    }

    /**
     * 删除科目。
     * <p>
     * 若该科目下已存在学习记录，则拒绝删除并提示改用"归档"，
     * 以免统计数据的科目信息缺失。
     *
     * @throws IllegalStateException 科目下存在学习记录时抛出
     */
    public void delete(int subjectId) {
        int recordCount = subjectDao.countRecords(subjectId);
        if (recordCount > 0) {
            throw new IllegalStateException(
                    "该科目下已有 " + recordCount + " 条学习记录，无法删除。可改为「归档」以保留历史数据。");
        }
        subjectDao.delete(subjectId);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("科目名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 30) {
            throw new IllegalArgumentException("科目名称不能超过 30 个字符");
        }
        return trimmed;
    }

    private void ensureNameAvailable(String name, Integer excludeId) {
        Subject existing = subjectDao.findByName(name);
        if (existing != null && (excludeId == null || existing.getId() != excludeId)) {
            throw new IllegalArgumentException("科目「" + name + "」已存在");
        }
    }
}