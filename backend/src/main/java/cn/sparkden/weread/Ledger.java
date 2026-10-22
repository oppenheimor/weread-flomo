package cn.sparkden.weread;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.sparkden.weread.Models.*;

@Service
public class Ledger {
    private final JdbcTemplate db;
    private final InstanceGuard guard;
    public Ledger(JdbcTemplate db, InstanceGuard guard) { this.db=db; this.guard=guard; }
    public Map<String,String> thoughts(UUID user) {
        var result=new HashMap<String,String>();
        db.query("SELECT note_id,thought FROM note_thought WHERE user_id=?", row -> { result.put(row.getString(1),row.getString(2)); },user);
        return result;
    }
    public void saveThought(UUID user,String noteId,String thought) {
        guard.check();
        db.update("INSERT INTO note_thought(user_id,note_id,thought) VALUES (?,?,?) ON CONFLICT (user_id,note_id) DO UPDATE SET thought=EXCLUDED.thought,updated_at=now()",user,noteId,thought);
    }
    public Set<String> dismissedSources(UUID user) {
        return new HashSet<>(db.queryForList("SELECT source_key FROM dismissed_source WHERE user_id=?", String.class, user));
    }
    @Transactional public void dismiss(UUID user, Note note) {
        guard.check();
        for (String key : note.sourceKeys()) db.update("INSERT INTO dismissed_source(user_id,source_key) VALUES (?,?) ON CONFLICT DO NOTHING", user, key);
    }
    public String status(UUID user, Note note) {
        var statuses=new HashSet<String>();
        for(String key:note.sourceKeys()) statuses.addAll(db.queryForList("SELECT d.status FROM delivery_source s JOIN delivery d ON d.user_id=s.user_id AND d.note_id=s.note_id WHERE s.user_id=? AND s.source_key=?",String.class,user,key));
        if(statuses.contains("uncertain")) return "uncertain";
        if(statuses.contains("sending")) return "sending";
        return statuses.contains("sent")?"sent":"ready";
    }
    @Transactional public void reserve(UUID user, Note note) {
        guard.check();
        if(!status(user,note).equals("ready")) throw new ApiException(409,"笔记已发送或结果不确定，请重新拉取");
        db.update("INSERT INTO delivery(user_id,note_id,content,status) VALUES (?,?,?,'sending')",user,note.id(),note.content());
        for(String key:note.sourceKeys()) db.update("INSERT INTO delivery_source(user_id,source_key,note_id) VALUES (?,?,?)",user,key,note.id());
    }
    /** 每个来源键的投递状态，供书架按书统计；不涉及正文。 */
    public Map<String,String> sourceStatuses(UUID user) {
        var result=new HashMap<String,String>();
        db.query("SELECT s.source_key,d.status FROM delivery_source s JOIN delivery d ON d.user_id=s.user_id AND d.note_id=s.note_id WHERE s.user_id=?",
            row -> { result.put(row.getString(1),row.getString(2)); },user);
        return result;
    }
    /** 用户核对 flomo 后处理结果不确定的记录：已存在则标记已发送，不存在则移除记录、回到待发送（不自动重发）。 */
    @Transactional public void resolveUncertain(UUID user, Note note, boolean delivered) {
        guard.check();
        var ids=new LinkedHashSet<String>();
        for(String key:note.sourceKeys()) ids.addAll(db.queryForList("SELECT d.note_id FROM delivery_source s JOIN delivery d ON d.user_id=s.user_id AND d.note_id=s.note_id WHERE s.user_id=? AND s.source_key=? AND d.status='uncertain'",String.class,user,key));
        if(ids.isEmpty()) throw new ApiException(409,"这条笔记不是结果不确定状态，请重新拉取");
        for(String id:ids) {
            if(delivered) db.update("UPDATE delivery SET status='sent',updated_at=now() WHERE user_id=? AND note_id=? AND status='uncertain'",user,id);
            else { db.update("DELETE FROM delivery_source WHERE user_id=? AND note_id=?",user,id); db.update("DELETE FROM delivery WHERE user_id=? AND note_id=? AND status='uncertain'",user,id); }
        }
    }
    public int sentCount(UUID user,List<String> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size()) throw new ApiException(400,"请选择 1 到 100 条不重复的笔记");
        var args=new ArrayList<Object>(); args.add(user); args.addAll(ids);
        return db.queryForObject("SELECT count(*) FROM delivery WHERE user_id=? AND status='sent' AND note_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",Integer.class,args.toArray());
    }
    public String ownStatus(UUID user,String id) {
        var values=db.queryForList("SELECT status FROM delivery WHERE user_id=? AND note_id=?",String.class,user,id);
        return values.isEmpty()?"ready":values.getFirst();
    }
    /** Organized results have their own delivery identity. Previously sent originals never block them. */
    @Transactional public void reserveOrganized(UUID user,String id,String content,List<Note> sources) {
        guard.check();
        if(!ownStatus(user,id).equals("ready")) throw new ApiException(409,"这条整理结果已发送或需核对");
        for(var source:sources) if(Set.of("sending","uncertain").contains(status(user,source))) throw new ApiException(409,"来源存在需核对的发送，请先处理");
        db.update("INSERT INTO delivery(user_id,note_id,content,status) VALUES (?,?,?,'sending')",user,id,content);
        for(var source:sources) for(String key:source.sourceKeys()) {
            // Unique source ledger keeps already-sent history unchanged; only ready sources are reserved.
            db.update("INSERT INTO delivery_source(user_id,source_key,note_id) VALUES (?,?,?) ON CONFLICT(user_id,source_key) DO NOTHING",user,key,id);
        }
    }
    @Transactional public void resolveOrganized(UUID user,String id,boolean delivered) {
        guard.check();
        if(!ownStatus(user,id).equals("uncertain")) throw new ApiException(409,"这条结果不处于需核对状态");
        if(delivered) finish(user,id,"sent");
        else { db.update("DELETE FROM delivery_source WHERE user_id=? AND note_id=?",user,id); db.update("DELETE FROM delivery WHERE user_id=? AND note_id=? AND status='uncertain'",user,id); }
    }
    public void finish(UUID user,String id,String status) {
        if(!Set.of("sent","uncertain").contains(status)) throw new IllegalArgumentException();
        if(db.update("UPDATE delivery SET status=?,updated_at=now() WHERE user_id=? AND note_id=?",status,user,id)!=1) throw new IllegalStateException("发送账本记录缺失");
    }
}
