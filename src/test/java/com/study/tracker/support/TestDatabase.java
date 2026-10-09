package com.study.tracker.support;

import com.study.tracker.config.UserSession;
import com.study.tracker.dao.DBUtil;
import com.study.tracker.model.User;
import com.study.tracker.service.UserService;
import com.study.tracker.util.PathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用数据库工具。
 * <p>
 * 每个测试都指向 target/test-data 下的独立目录，因此不会碰到开发时使用的
 * data\data.db。数据库连接串由 DBUtil 在每次 initialize 时重新计算，
 * 所以同一个 JVM 里连续切换目录也是安全的。
 */
public final class TestDatabase {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestDatabase() {
    }

    /**
     * 准备一个空数据库，并清除登录状态。
     *
     * @param name 用例名，用于区分目录
     * @return 数据目录
     */
    public static Path reset(String name) {
        try {
            Path dir = Path.of("target", "test-data", name + "-" + COUNTER.incrementAndGet())
                    .toAbsolutePath();
            // 计数器每次 JVM 都从 1 开始，因此先清空目录，避免复用上一次运行留下的数据
            deleteRecursively(dir);
            Files.createDirectories(dir);
            System.setProperty(PathUtil.PROP_DATA_DIR, dir.toString());
            UserSession.logout();
            DBUtil.initialize();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("准备测试数据库失败", e);
        }
    }

    /** 递归删除目录（不存在时直接返回）。 */
    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** 准备数据库，并创建一个已登录的管理员账户。 */
    public static User resetWithAdmin(String name) {
        reset(name);
        User admin = new UserService().initializeAdmin("tester", "secret123", "secret123");
        UserSession.login(admin);
        return admin;
    }
}
