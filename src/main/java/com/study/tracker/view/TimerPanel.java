package com.study.tracker.view;

import com.study.tracker.config.AppConfig;
import com.study.tracker.config.AppearanceManager;
import com.study.tracker.model.StudyRecord;
import com.study.tracker.model.Subject;
import com.study.tracker.model.TimerMode;
import com.study.tracker.service.SubjectService;
import com.study.tracker.service.TimerService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * 学习计时面板。
 * <p>
 * 同时支持自由计时与番茄钟两种模式：
 * <ul>
 *   <li>自由计时：大号数字显示已用时间，正向累加；</li>
 *   <li>番茄钟：显示当前阶段剩余时间，专注阶段结束自动记账并进入休息阶段。</li>
 * </ul>
 * 界面不保存任何计时状态，全部由 {@link TimerService} 维护。
 */
public class TimerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 计时显示字体大小。 */
    private static final int TIME_FONT_SIZE = 56;

    private final SubjectService subjectService = new SubjectService();
    private final TimerService timerService = new TimerService();

    private final JComboBox<Subject> subjectCombo = new JComboBox<>();
    private final JComboBox<TimerMode> modeCombo = new JComboBox<>(TimerMode.values());
    private final JButton pomodoroSettingButton = new JButton("番茄钟设置");

    private final JLabel timeLabel = new JLabel("00:00:00", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("请选择科目后开始计时", SwingConstants.CENTER);
    private final JLabel todayLabel = new JLabel();

    private final JButton startButton = new JButton("开始计时");
    private final JButton pauseButton = new JButton("暂停");
    private final JButton resumeButton = new JButton("继续");
    private final JButton finishButton = new JButton("结束并保存");

    /** 每秒刷新一次显示。 */
    private final Timer refreshTimer = new Timer(1000, e -> refreshTimeDisplay());

    /** 防止模态对话框期间重复触发阶段切换。 */
    private boolean handlingPhaseEnd;

    public TimerPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        bindActions();
        reloadSubjects();
        updateButtonState();
        updateTimeColor();
    }

    /** 顶部：科目与模式选择。 */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        panel.add(new JLabel("科目："));
        subjectCombo.setPreferredSize(new Dimension(170, 28));
        panel.add(subjectCombo);

        panel.add(Box.createHorizontalStrut(18));
        panel.add(new JLabel("模式："));
        modeCombo.setPreferredSize(new Dimension(130, 28));
        panel.add(modeCombo);

        panel.add(Box.createHorizontalStrut(10));
        panel.add(pomodoroSettingButton);

        JButton refreshButton = new JButton("刷新科目");
        refreshButton.addActionListener(e -> reloadSubjects());
        panel.add(refreshButton);

        return panel;
    }

    /** 中部：计时显示与状态提示。 */
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        timeLabel.setFont(new Font(AppConfig.UI_FONT_NAME, Font.BOLD, TIME_FONT_SIZE));
        statusLabel.setFont(AppearanceManager.uiFont(Font.PLAIN, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(timeLabel, gbc);

        gbc.gridy = 1;
        panel.add(statusLabel, gbc);

        return panel;
    }

    /** 底部：控制按钮与今日统计。 */
    private JPanel createBottomPanel() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        controls.add(startButton);
        controls.add(pauseButton);
        controls.add(resumeButton);
        controls.add(finishButton);

        todayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        todayLabel.setFont(AppearanceManager.uiFont(Font.PLAIN, -1));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(controls);
        panel.add(todayLabel);
        return panel;
    }

    /** 绑定按钮事件。 */
    private void bindActions() {
        startButton.addActionListener(e -> onStart());
        pauseButton.addActionListener(e -> onPause());
        resumeButton.addActionListener(e -> onResume());
        finishButton.addActionListener(e -> onFinish());
        pomodoroSettingButton.addActionListener(e -> onPomodoroSetting());
        modeCombo.addActionListener(e -> updateButtonState());
    }

    /** 重新加载科目下拉框。 */
    public void reloadSubjects() {
        Subject selected = (Subject) subjectCombo.getSelectedItem();

        subjectCombo.removeAllItems();
        List<Subject> subjects = subjectService.list(false);
        for (Subject subject : subjects) {
            subjectCombo.addItem(subject);
        }

        if (selected != null) {
            for (int i = 0; i < subjectCombo.getItemCount(); i++) {
                if (subjectCombo.getItemAt(i).getId() == selected.getId()) {
                    subjectCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        refreshTodayTotal();
    }

    private void onStart() {
        Subject subject = (Subject) subjectCombo.getSelectedItem();
        if (subject == null) {
            showWarning("请先添加科目，再开始计时");
            return;
        }

        try {
            TimerMode mode = (TimerMode) modeCombo.getSelectedItem();
            if (mode == TimerMode.POMODORO) {
                timerService.startPomodoro(subject.getId(),
                        timerService.getFocusMinutes(), timerService.getBreakMinutes());
            } else {
                timerService.start(subject.getId(), TimerMode.FREE);
            }
            refreshTimer.start();
            refreshTimeDisplay();
            updateButtonState();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void onPause() {
        try {
            timerService.pause();
            refreshTimeDisplay();
            updateButtonState();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void onResume() {
        try {
            timerService.resume();
            refreshTimeDisplay();
            updateButtonState();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 结束计时：自由计时直接结算；番茄钟则按"放弃"处理并提示。 */
    private void onFinish() {
        boolean pomodoro = timerService.getMode() == TimerMode.POMODORO;
        String prompt = pomodoro
                ? "番茄钟尚未结束，现在停止将记为「未完整完成」。\n可填写备注（留空即可）："
                : "本次有效学习时长：" + formatDuration(timerService.getElapsedSeconds()) + "\n可填写备注（留空即可）：";

        String remark = JOptionPane.showInputDialog(this, prompt, "结束计时", JOptionPane.PLAIN_MESSAGE);
        if (remark == null) {
            return;
        }

        try {
            if (pomodoro) {
                StudyRecord saved = timerService.abandonPomodoro(remark);
                String message = saved == null
                        ? "本次番茄钟已取消，未产生记录。"
                        : "已保存一条未完整完成的记录：" + saved.getDurationMinutes() + " 分钟";
                JOptionPane.showMessageDialog(this, message, "已结束", JOptionPane.INFORMATION_MESSAGE);
            } else {
                StudyRecord record = timerService.finish(remark);
                JOptionPane.showMessageDialog(this,
                        "已保存本次学习记录：" + record.getDurationMinutes() + " 分钟",
                        "保存成功", JOptionPane.INFORMATION_MESSAGE);
            }

            refreshTimer.stop();
            refreshTimeDisplay();
            refreshTodayTotal();
            updateButtonState();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    /** 番茄钟参数设置。 */
    private void onPomodoroSetting() {
        if (!timerService.isIdle()) {
            showWarning("请先结束当前计时，再修改番茄钟设置");
            return;
        }

        JSpinner focusSpinner = new JSpinner(
                new SpinnerNumberModel(timerService.getFocusMinutes(), 1, 180, 5));
        JSpinner breakSpinner = new JSpinner(
                new SpinnerNumberModel(timerService.getBreakMinutes(), 1, 60, 1));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("专注时长(分钟)："), gbc);
        gbc.gridx = 1;
        form.add(focusSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("休息时长(分钟)："), gbc);
        gbc.gridx = 1;
        form.add(breakSpinner, gbc);

        int result = JOptionPane.showConfirmDialog(this, form, "番茄钟设置",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        timerService.setFocusMinutes((Integer) focusSpinner.getValue());
        timerService.setBreakMinutes((Integer) breakSpinner.getValue());
        refreshTimeDisplay();
        updateButtonState();
    }

    /** 每秒刷新显示。 */
    private void refreshTimeDisplay() {
        TimerMode mode = timerService.getMode();
        TimerService.PomodoroPhase phase = timerService.getPhase();

        if (mode == TimerMode.POMODORO && phase != TimerService.PomodoroPhase.NONE) {
            // 番茄钟：到时则自动切换阶段
            if (timerService.isRunning() && timerService.isPhaseFinished() && !handlingPhaseEnd) {
                handlingPhaseEnd = true;
                try {
                    handlePhaseFinished(phase);
                } finally {
                    handlingPhaseEnd = false;
                }
                return;
            }

            timeLabel.setText(formatDuration(timerService.getRemainingSeconds()));
            statusLabel.setText(switch (phase) {
                case FOCUS -> "专注中（剩余 " + timerService.getFocusMinutes() + " 分钟阶段）"
                        + " · 已完成 " + timerService.getCompletedPomodoros() + " 个番茄"
                        + (timerService.isPaused() ? " · 已暂停" : "");
                case BREAK -> "休息中 · 已完成 " + timerService.getCompletedPomodoros() + " 个番茄"
                        + (timerService.isPaused() ? " · 已暂停" : "");
                default -> "";
            });
        } else {
            timeLabel.setText(formatDuration(timerService.getElapsedSeconds()));
            statusLabel.setText(switch (timerService.getState()) {
                case IDLE -> "请选择科目后开始计时";
                case RUNNING -> "计时中……已暂停 " + timerService.getPauseCount() + " 次";
                case PAUSED -> "已暂停（暂停时间不计入学习时长）";
            });
        }

        updateTimeColor();
    }

    /** 处理番茄钟阶段结束。 */
    private void handlePhaseFinished(TimerService.PomodoroPhase finishedPhase) {
        if (finishedPhase == TimerService.PomodoroPhase.FOCUS) {
            String remark = JOptionPane.showInputDialog(this,
                    "专注阶段完成！这次学习的是「" + currentSubjectName() + "」。\n可填写本次备注（留空即可）：",
                    "专注完成", JOptionPane.PLAIN_MESSAGE);
            if (remark == null) {
                remark = "";
            }
            try {
                StudyRecord record = timerService.completeFocusPhase(remark);
                refreshTodayTotal();
                JOptionPane.showMessageDialog(this,
                        "已记录 " + record.getDurationMinutes() + " 分钟学习时长。\n接下来休息 "
                                + timerService.getBreakMinutes() + " 分钟。",
                        "番茄完成", JOptionPane.INFORMATION_MESSAGE);
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
                return;
            }
        } else {
            timerService.completeBreakPhase();
            JOptionPane.showMessageDialog(this,
                    "休息结束，开始下一个番茄钟吧！",
                    "继续专注", JOptionPane.INFORMATION_MESSAGE);
        }

        refreshTimeDisplay();
        updateButtonState();
    }

    /** 根据状态更新按钮可用性。 */
    private void updateButtonState() {
        boolean idle = timerService.isIdle();
        boolean running = timerService.isRunning();
        boolean paused = timerService.isPaused();

        startButton.setEnabled(idle);
        pauseButton.setEnabled(running);
        resumeButton.setEnabled(paused);
        finishButton.setEnabled(running || paused);

        subjectCombo.setEnabled(idle);
        modeCombo.setEnabled(idle);
        pomodoroSettingButton.setEnabled(idle);
    }

    /** 自由计时与番茄钟使用不同颜色区分。 */
    private void updateTimeColor() {
        boolean pomodoro = timerService.getMode() == TimerMode.POMODORO
                && timerService.getPhase() != TimerService.PomodoroPhase.NONE;
        if (timerService.isIdle()) {
            timeLabel.setForeground(null);
        } else if (pomodoro) {
            timeLabel.setForeground(timerService.getPhase() == TimerService.PomodoroPhase.FOCUS
                    ? new Color(0xC0392B) : new Color(0x27866F));
        } else {
            timeLabel.setForeground(new Color(0x2D7FF9));
        }
    }

    /** 刷新今日累计学习时长。 */
    private void refreshTodayTotal() {
        int minutes = timerService.getTodayMinutes();
        todayLabel.setText(String.format("今日累计学习：%d 小时 %d 分钟", minutes / 60, minutes % 60));
    }

    private String currentSubjectName() {
        Subject subject = (Subject) subjectCombo.getSelectedItem();
        return subject == null ? "未选择" : subject.getName();
    }

    /** 把秒数格式化为 HH:mm:ss。 */
    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}