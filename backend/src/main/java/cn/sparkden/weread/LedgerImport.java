package cn.sparkden.weread;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅启动参数启用的离线迁移，没有 HTTP 导入或清空接口。 */
@Component
@Order(20)
public class LedgerImport implements ApplicationRunner {
    private final String path;
    private final String username;
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    public LedgerImport(@Value("${app.import-ledger:}") String path, @Value("${app.import-user:}") String username, JdbcTemplate db,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.username=username; this.path=path; this.db=db; this.mapper=mapper; this.transaction=new TransactionTemplate(manager);
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        if(path.isBlank()) return;
        if(Files.size(Path.of(path))>50*1024*1024) throw new IllegalStateException("迁移文件超过 50MiB");
        importData(mapper.readTree(Files.readString(Path.of(path))));
    }
    public void importData(JsonNode root) {
        if(root.path("version").asInt()!=1 || !root.path("deliveries").isArray()) throw new IllegalArgumentException("迁移格式错误");
        var users = username.isBlank()
            ? db.queryForList("SELECT id FROM app_user", UUID.class)
            : db.queryForList("SELECT id FROM app_user WHERE username=?", UUID.class, username);
        if (users.size()!=1) throw new IllegalStateException("请通过 --app.import-user 指定唯一的迁移目标账号");
        UUID user=users.getFirst();
        transaction.executeWithoutResult(tx -> {
            for(var row:root.get("deliveries")) {
                String id=row.path("noteId").asText(""); String status=row.path("status").asText("");
                String content=row.path("content").asText(""); var keys=row.path("sourceKeys");
                if(id.isBlank() || id.length()>4096 || content.isBlank() || content.length()>100000 || !Set.of("sent","uncertain").contains(status) || !keys.isArray() || keys.isEmpty()) throw new IllegalArgumentException("迁移记录无效");
                var unique=new HashSet<String>();
                for(var k:keys) if(!k.isTextual() || k.asText().isBlank() || !unique.add(k.asText())) throw new IllegalArgumentException("迁移来源键无效");
                if(!unique.contains(id)) throw new IllegalArgumentException("迁移来源缺少主记录键");
                int inserted=db.update("INSERT INTO delivery(user_id,note_id,content,status) VALUES (?,?,?,?) ON CONFLICT(user_id,note_id) DO NOTHING",user,id,content,status);
                if(inserted==1) for(String key:unique) db.update("INSERT INTO delivery_source(user_id,source_key,note_id) VALUES (?,?,?)",user,key,id);
            }
        });
    }
}
