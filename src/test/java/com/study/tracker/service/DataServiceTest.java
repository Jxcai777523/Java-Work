package com.study.tracker.service;

import com.study.tracker.model.Subject;
import com.study.tracker.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("数据备份、恢复与导出")
class DataServiceTest {

    private final DataService dataService = new DataService();
    private Path workDir;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.resetWithAdmin("data");
        workDir = Files.createDirectories(Path.of("target", "test-data", "files"));

        Subject subject = new SubjectService().create("测试科目", "#123456", 60);
        LocalDateTime start = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0);
        new RecordService().addManual(subject.getId(), start, start.plusMinutes(45), "备注,带逗号");
    }

    @Test
    @DisplayName("备份文件是本系统的合法数据库")
    void backupProducesValidDatabase() {
        File backup = workDir.resolve("backup.db").toFile();
        dataService.backup(backup);

        assertTrue(backup.isFile());
        assertTrue(backup.length() > 0);
        assertDoesNotThrow(() -> dataService.validateBackup(backup));
    }

    @Test
    @DisplayName("恢复后回到备份时刻的数据，并留下原库副本")
    void restoreRollsBackToBackupState() {
        File backup = workDir.resolve("backup-restore.db").toFile();
        dataService.backup(backup);

        Subject extra = new SubjectService().create("备份后新增", "#654321", 60);
        LocalDateTime start = LocalDateTime.now().withHour(14).withMinute(0).withSecond(0).withNano(0);
        new RecordService().addManual(extra.getId(), start, start.plusMinutes(30), null);
        assertEquals(2, new RecordService().search(null, null, null).size());

        Path safety = dataService.restore(backup);

        assertEquals(1, new RecordService().search(null, null, null).size());
        assertTrue(Files.exists(safety));
    }

    @Test
    @DisplayName("导出的 CSV 带 BOM 与表头，逗号被正确转义")
    void exportCsvContainsHeaderAndEscapedFields() throws Exception {
        File csv = workDir.resolve("records.csv").toFile();
        int exported = dataService.exportRecords(csv);

        assertEquals(1, exported);
        List<String> lines = Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("\uFEFF"));
        assertTrue(lines.get(0).contains("开始时间"));
        assertTrue(lines.get(1).contains("\"备注,带逗号\""));
    }

    @Test
    @DisplayName("普通文件不会被当成备份")
    void nonDatabaseFileIsRejected() throws Exception {
        File notDb = workDir.resolve("not-a-db.txt").toFile();
        Files.writeString(notDb.toPath(), "这不是数据库");

        assertThrows(IllegalArgumentException.class, () -> dataService.validateBackup(notDb));
    }
}
