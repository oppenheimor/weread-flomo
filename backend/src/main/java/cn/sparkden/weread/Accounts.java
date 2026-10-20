package cn.sparkden.weread;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@org.springframework.core.annotation.Order(10)
public class Accounts implements UserDetailsService, ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public Accounts(JdbcTemplate db, PasswordEncoder encoder,
            @Value("${app.admin-username}") String username, @Value("${app.admin-password}") String password) {
        this.db = db; this.encoder = encoder; this.username = username; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT count(*) FROM app_user", Integer.class) == 0) {
            if (!username.matches("[A-Za-z0-9_.-]{3,64}") || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("首次启动请配置 ADMIN_USERNAME（3–64 位）和 ADMIN_PASSWORD（至少 12 字符，最多 72 UTF-8 字节）");
            db.update("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)", UUID.randomUUID(), username, encoder.encode(password));
        }
    }
    @Override public UserDetails loadUserByUsername(String name) {
        return db.query("SELECT username,password_hash FROM app_user WHERE username=?",
            (rs, n) -> User.withUsername(rs.getString(1)).password(rs.getString(2)).roles("OWNER").build(), name)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
    public UUID id(String name) {
        return db.queryForObject("SELECT id FROM app_user WHERE username=?", UUID.class, name);
    }
    public void register(String name, String secret) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{3,64}"))
            throw new ApiException(400,"账号需为 3–64 位字母、数字、下划线、点或短横线");
        if (secret == null || secret.isBlank() || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400,"密码不能为空，最多 72 个 UTF-8 字节");
        try {
            db.update("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)", UUID.randomUUID(), name, encoder.encode(secret));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(409,"该账号已被注册");
        }
    }
    public boolean allowLogin(String name, String address) {
        return allowAttempt("login-ip:" + address, 100) && allowAttempt("login-user:" + name, 10);
    }
    public boolean allowRegistration(String address) { return allowAttempt("register:" + address, 5); }
    private boolean allowAttempt(String key, int limit) {
        // 只使用连接地址，不信任客户端传来的 X-Forwarded-For；哈希避免保存账号/IP 明文。
        String bucket;
        try { bucket = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        db.update("DELETE FROM login_throttle WHERE started_at < now()-interval '1 day'");
        return Boolean.TRUE.equals(db.queryForObject("""
            INSERT INTO login_throttle(bucket,started_at,attempts) VALUES (?,now(),1)
            ON CONFLICT(bucket) DO UPDATE SET
              attempts=CASE WHEN login_throttle.started_at < now()-interval '5 minutes' THEN 1 ELSE login_throttle.attempts+1 END,
              started_at=CASE WHEN login_throttle.started_at < now()-interval '5 minutes' THEN now() ELSE login_throttle.started_at END
            RETURNING attempts <= ?
            """, Boolean.class, bucket, limit));
    }
}
