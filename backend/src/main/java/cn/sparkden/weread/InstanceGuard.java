package cn.sparkden.weread;

import java.sql.*;
import javax.sql.DataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class InstanceGuard implements ApplicationRunner {
    private final DataSource source;
    private Connection lock;
    public InstanceGuard(DataSource source) { this.source=source; }
    @Override public synchronized void run(ApplicationArguments args) throws SQLException {
        lock=source.getConnection();
        try(var statement=lock.createStatement(); var result=statement.executeQuery("SELECT pg_try_advisory_lock(193742,18081)")) {
            if(!result.next() || !result.getBoolean(1)) throw new IllegalStateException("已有本应用实例持有数据库锁，禁止双活");
        }
        try(var statement=lock.createStatement()) {
            statement.executeUpdate("UPDATE delivery SET status='uncertain',updated_at=now() WHERE status='sending'");
            statement.executeUpdate("UPDATE ai_chapter SET state='failed',drafts='[]',error='服务重启中断了整理，请手动重试；旧结果未改动',updated_at=now() WHERE state='running'");
        }
    }
    public synchronized void check() {
        try { if(lock==null || !lock.isValid(2)) throw new IllegalStateException(); }
        catch(Exception e) { throw new ApiException(503,"数据库实例锁已失效，请重启服务，不会继续发送"); }
    }
    @PreDestroy public synchronized void close() throws SQLException { if(lock!=null) lock.close(); }
}
