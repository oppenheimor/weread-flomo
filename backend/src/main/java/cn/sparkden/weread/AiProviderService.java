package cn.sparkden.weread;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.sparkden.weread.AiModels.*;

@Service
public class AiProviderService {
    private final JdbcTemplate db;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    public AiProviderService(JdbcTemplate db,SecretCipher cipher,ObjectMapper mapper) { this.db=db; this.cipher=cipher; this.mapper=mapper; }
    private List<String> models(String json) { try { return mapper.readValue(json,new TypeReference<List<String>>() {}); } catch(Exception e) { throw new IllegalStateException("模型配置损坏"); } }
    public Settings settings(UUID user) {
        var providers=db.query("SELECT * FROM ai_provider WHERE user_id=? ORDER BY name",(r,i) -> new Provider(r.getString("id"),r.getString("name"),r.getString("protocol"),r.getString("base_url"),!r.getString("ciphertext").isEmpty(),models(r.getString("models"))),user);
        var defaults=db.query("SELECT * FROM ai_default WHERE user_id=?",(r,i) -> new DefaultModel(r.getString("provider_id"),r.getString("model")),user);
        var selected=defaults.isEmpty()?null:defaults.getFirst();
        boolean control=selected!=null && !"magpie".equals(selected.providerId()) && providers.stream().anyMatch(p -> p.id().equals(selected.providerId()) && AiTransport.supportsThinking(p.protocol(),selected.model()));
        return new Settings(providers,selected,control);
    }
    @Transactional public Settings save(UUID user,ProviderChange change) {
        if(change.id()==null || !change.id().matches("[a-zA-Z0-9_-]{1,80}") || change.name()==null || change.name().isBlank() || change.name().length()>120) throw new ApiException(400,"服务商名称或 ID 无效");
        if(change.protocol()==null || !Set.of("openai","anthropic","google").contains(change.protocol())) throw new ApiException(400,"请选择接口协议");
        boolean local="magpie".equals(change.id());
        if(local && !"openai".equals(change.protocol())) throw new ApiException(400,"Magpie 必须使用 OpenAI 兼容协议");
        String base=local?browserUrl(change.baseUrl()):AiTransport.validUrl(change.baseUrl()).toString().replaceAll("/+$","");
        if(change.apiKey()!=null && (change.apiKey().length()>4096 || change.apiKey().chars().anyMatch(c -> c<32 || c==127))) throw new ApiException(400,"API Key 格式无效");
        var ids=change.models()==null?List.<String>of():change.models();
        if(ids.size()>500 || ids.stream().anyMatch(m -> !validModel(m))) throw new ApiException(400,"模型 ID 无效或数量过多");
        String key=change.apiKey()==null?"":change.apiKey().strip();
        var previous=db.queryForList("SELECT ciphertext FROM ai_provider WHERE user_id=? AND id=?",String.class,user,change.id());
        String ciphertext=!key.isEmpty()?cipher.encrypt(user,"ai:"+change.id(),key):previous.isEmpty()?"":previous.getFirst();
        try {
            db.update("INSERT INTO ai_provider(user_id,id,name,protocol,base_url,ciphertext,models) VALUES (?,?,?,?,?,?,?) ON CONFLICT(user_id,id) DO UPDATE SET name=EXCLUDED.name,protocol=EXCLUDED.protocol,base_url=EXCLUDED.base_url,ciphertext=EXCLUDED.ciphertext,models=EXCLUDED.models",user,change.id(),change.name().strip(),change.protocol(),base,ciphertext,mapper.writeValueAsString(new LinkedHashSet<>(ids)));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        return settings(user);
    }
    public String magpieKey(UUID user) {
        var rows=db.queryForList("SELECT ciphertext FROM ai_provider WHERE user_id=? AND id='magpie'",String.class,user);
        if(rows.isEmpty() || rows.getFirst().isEmpty()) throw new ApiException(400,"请先保存 Magpie 网关密钥");
        return cipher.decrypt(user,"ai:magpie",rows.getFirst());
    }
    public static boolean validModel(String id) { return id!=null && id.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") && !id.contains(".."); }
    @Transactional public Settings setDefault(UUID user,DefaultModel selection) {
        if("magpie".equals(selection.providerId())) {
            if(!validModel(selection.model()) || settings(user).providers().stream().noneMatch(p -> p.id().equals("magpie"))) throw new ApiException(400,"请先保存 Magpie 配置并选择模型");
        } else connection(user,selection.providerId(),selection.model());
        db.update("INSERT INTO ai_default(user_id,provider_id,model) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET provider_id=EXCLUDED.provider_id,model=EXCLUDED.model",user,selection.providerId(),selection.model());
        return settings(user);
    }
    public static String browserUrl(String raw) {
        try {
            var uri=java.net.URI.create(raw.strip());
            if(!Set.of("http","https").contains(uri.getScheme()) || !Set.of("localhost","127.0.0.1").contains(uri.getHost()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().replaceAll("/+$","").equals("/v1")) throw new IllegalArgumentException();
            return uri.toString().replaceAll("/+$","");
        } catch(Exception e) { throw new ApiException(400,"Magpie 请使用 localhost 或 127.0.0.1 的 /v1 本机网关地址"); }
    }
    public Connection connection(UUID user,String provider,String model) {
        if("magpie".equals(provider)) throw new ApiException(400,"Magpie 必须由浏览器连接，服务器不访问本地网关");
        if(!validModel(model)) throw new ApiException(400,"请填写有效模型 ID");
        var rows=db.query("SELECT * FROM ai_provider WHERE user_id=? AND id=?",(r,i) -> new Connection(r.getString("protocol"),r.getString("base_url"),r.getString("ciphertext").isEmpty()?"":cipher.decrypt(user,"ai:"+provider,r.getString("ciphertext")),model),user,provider);
        if(rows.isEmpty()) throw new ApiException(400,"请先保存服务商配置");
        return rows.getFirst();
    }
    public Connection defaultConnection(UUID user) {
        DefaultModel selected=settings(user).defaultModel();
        if(selected==null) throw new ApiException(400,"请先设置默认 AI 模型");
        return connection(user,selected.providerId(),selected.model());
    }
    public void delete(UUID user,String provider) { db.update("DELETE FROM ai_provider WHERE user_id=? AND id=?",user,provider); }
}
