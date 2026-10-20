package cn.sparkden.weread;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Credentials {
    public record Change(String wereadApiKey, String flomoApiUrl) {
        @Override public String toString() { return "Credentials.Change[REDACTED]"; }
    }
    public record Revealed(String value) {
        @Override public String toString() { return "Credentials.Revealed[REDACTED]"; }
    }
    public Revealed reveal(UUID user, String provider) {
        if (provider == null || !Set.of("weread", "flomo").contains(provider)) throw new ApiException(400, "未知的凭证类型");
        return new Revealed(required(user, provider));
    }
    private final JdbcTemplate db;
    private final SecretCipher cipher;
    public Credentials(JdbcTemplate db, SecretCipher cipher) { this.db = db; this.cipher = cipher; }
    public Map<String,Object> status(UUID user) {
        var providers = db.queryForList("SELECT provider FROM user_credential WHERE user_id=?", String.class, user);
        return Map.of("wereadConfigured", providers.contains("weread"), "flomoConfigured", providers.contains("flomo"), "sendingEnabled", providers.contains("flomo"));
    }
    public String required(UUID user, String provider) {
        var rows = db.queryForList("SELECT ciphertext FROM user_credential WHERE user_id=? AND provider=?", String.class, user, provider);
        if (rows.isEmpty()) throw new ApiException(400, "请先在账号设置中配置" + (provider.equals("weread") ? "微信读书 API Key" : "flomo API 地址"));
        return cipher.decrypt(user, provider, rows.getFirst());
    }
    @Transactional public void save(UUID user, Change change) {
        if (change.wereadApiKey() == null && change.flomoApiUrl() == null) throw new ApiException(400, "请填写需要更新的凭证");
        // 先全部校验，再保存。未填写的字段保持不变，删除必须走明确的删除操作。
        if (change.wereadApiKey() != null && (change.wereadApiKey().length() > 4096 || !change.wereadApiKey().strip().matches("wrk-[A-Za-z0-9_-]+"))) throw new ApiException(400, "微信读书 API Key 格式不正确，请从官方入口重新复制");
        if (change.flomoApiUrl() != null && (change.flomoApiUrl().length() > 2048 || !RemoteApis.validFlomoUrl(change.flomoApiUrl().strip()))) throw new ApiException(400, "请填写 flomo 官方完整专属地址：https://flomoapp.com/iwh/…/…/");
        if (change.wereadApiKey() != null) put(user, "weread", change.wereadApiKey().strip());
        if (change.flomoApiUrl() != null) put(user, "flomo", change.flomoApiUrl().strip());
    }
    private void put(UUID user, String provider, String value) {
        db.update("INSERT INTO user_credential(user_id,provider,ciphertext) VALUES (?,?,?) ON CONFLICT(user_id,provider) DO UPDATE SET ciphertext=excluded.ciphertext,updated_at=now()", user, provider, cipher.encrypt(user, provider, value));
    }
    public void delete(UUID user, String provider) {
        if (!Set.of("weread", "flomo").contains(provider)) throw new ApiException(400, "未知的凭证类型");
        db.update("DELETE FROM user_credential WHERE user_id=? AND provider=?", user, provider);
    }
}
