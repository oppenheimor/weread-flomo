package cn.sparkden.weread;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import static cn.sparkden.weread.AiModels.*;

@Service
public class AiService {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final NotebookService notebook;
    private final Ledger ledger;
    private final RemoteApis remote;
    private final AiProviderService providers;
    private final AiTransport transport;
    private final AiOrganizer organizer;
    private final TransactionTemplate tx;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Semaphore capacity=new Semaphore(8);
    public AiService(JdbcTemplate db,ObjectMapper mapper,NotebookService notebook,Ledger ledger,RemoteApis remote,AiProviderService providers,AiTransport transport,AiOrganizer organizer,TransactionTemplate tx) {
        this.db=db; this.mapper=mapper; this.notebook=notebook; this.ledger=ledger; this.remote=remote; this.providers=providers; this.transport=transport; this.organizer=organizer; this.tx=tx;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Snapshot snapshot(String value) { try { return mapper.readValue(value,Snapshot.class); } catch(Exception e) { throw new IllegalStateException("整理来源损坏"); } }
    private List<Draft> drafts(String value) {
        try { return mapper.readValue(value,mapper.getTypeFactory().constructCollectionType(List.class,Draft.class)); }
        catch(Exception e) { return List.of(); }
    }
    private List<Card> cards(UUID user,String book,String chapter) {
        return db.query("SELECT * FROM ai_note WHERE user_id=? AND book_id=? AND chapter_id=? ORDER BY created_at,position",(r,i) -> {
            Snapshot snap=snapshot(r.getString("snapshot")); String id=r.getString("id"); String status=ledger.ownStatus(user,id);
            if(snap.notes().size()==1 && status.equals("ready")) status=ledger.status(user,snap.notes().getFirst());
            return new Card(id,r.getString("title"),r.getString("body"),snap,status,r.getBoolean("edited"),r.getInt("version"));
        },user,book,chapter);
    }
    public List<Models.Chapter> chapters(UUID user,String book) {
        return db.query("SELECT DISTINCT ON (chapter_id) snapshot FROM ai_note WHERE user_id=? AND book_id=? ORDER BY chapter_id,created_at",(r,i) -> snapshot(r.getString(1)).chapter(),user,book);
    }
    private void expireBrowserJobs(UUID user) {
        db.update("UPDATE ai_chapter SET state='failed',error='本地整理已超时或页面已关闭，请重试',drafts='[]',client_snapshot=NULL,client_deadline=NULL WHERE user_id=? AND state='running' AND client_deadline<now()",user);
    }
    public ChapterState state(UUID user,String book,String chapter) {
        expireBrowserJobs(user);
        var rows=db.query("SELECT * FROM ai_chapter WHERE user_id=? AND book_id=? AND chapter_id=?",(r,i) -> new ChapterState(r.getString("state"),r.getString("error"),List.of(),new Progress(r.getString("phase"),r.getTimestamp("started_at")==null?null:r.getTimestamp("started_at").toInstant().toEpochMilli(),r.getInt("source_count"),r.getInt("output_chars"),r.getString("summary")),r.getString("state").equals("running")?drafts(r.getString("drafts")):List.of()),user,book,chapter);
        var state=rows.isEmpty()?new ChapterState("idle","",List.of(),null,List.of()):rows.getFirst();
        return new ChapterState(state.state(),state.error(),state.state().equals("running")?List.of():cards(user,book,chapter),state.progress(),state.drafts());
    }
    private void editable(UUID user,String book,String chapter) {
        if(db.queryForObject("SELECT count(*) FROM ai_chapter WHERE user_id=? AND book_id=? AND chapter_id=? AND state='running'",Integer.class,user,book,chapter)>0) throw new ApiException(409,"本章正在整理，暂时不能编辑或同步旧结果");
    }
    public Object start(UUID user,Start request) {
        return notebook.exclusive(() -> {
            expireBrowserJobs(user);
            var snap=notebook.aiSnapshot(user,request.previewId(),request.chapterId());
            String book=snap.book().bookId(),chapter=snap.chapter().id();
            editable(user,book,chapter);
            var old=cards(user,book,chapter);
            if(old.stream().anyMatch(c -> Set.of("uncertain","sending").contains(c.status()))) throw new ApiException(409,"请先核对本章发送结果，再重新整理");
            if(old.stream().anyMatch(c -> c.edited() && c.status().equals("ready")) && !request.replaceConfirmed()) throw new ApiException(409,"重新整理会覆盖已编辑的结果，请确认后继续");
            if(db.queryForObject("SELECT count(*) FROM ai_chapter WHERE user_id=? AND state='running'",Integer.class,user)>=2) throw new ApiException(429,"最多同时整理两个章节，请稍后再试");
            var settings=providers.settings(user);
            if(settings.defaultModel()!=null && settings.defaultModel().providerId().equals("magpie")) {
                if(request.deepThinking()) throw new ApiException(400,"Magpie 暂不支持显式切换深度思考");
                var provider=settings.providers().stream().filter(p -> p.id().equals("magpie")).findFirst().orElseThrow(() -> new ApiException(400,"请先配置 Magpie"));
                String base=AiProviderService.browserUrl(provider.baseUrl()),prompt=organizer.prompt(snap);
                UUID job=UUID.randomUUID();
                db.update("INSERT INTO ai_chapter(user_id,book_id,chapter_id,job_id,state,phase,started_at,source_count,client_snapshot,client_deadline) VALUES (?,?,?,?,'running','requesting',now(),?,?,now()+interval '5 minutes') ON CONFLICT(user_id,book_id,chapter_id) DO UPDATE SET job_id=EXCLUDED.job_id,state='running',phase='requesting',started_at=now(),source_count=EXCLUDED.source_count,output_chars=0,summary='',drafts='[]',error='',client_snapshot=EXCLUDED.client_snapshot,client_deadline=EXCLUDED.client_deadline,updated_at=now()",user,book,chapter,job,snap.notes().size(),json(snap));
                return new BrowserJob("browser",job,book,chapter,base,settings.defaultModel().model(),AiOrganizer.SYSTEM,prompt,state(user,book,chapter));
            }
            var connection=providers.defaultConnection(user).withThinking(request.deepThinking());
            if(request.deepThinking() && !AiTransport.supportsThinking(connection.protocol(),connection.model())) throw new ApiException(400,"当前模型尚不支持显式切换深度思考，请在模型设置中选择支持的模型");
            String prompt=organizer.prompt(snap);
            if(!capacity.tryAcquire()) throw new ApiException(429,"整理任务较多，请稍后重试");
            UUID job=UUID.randomUUID();
            try {
                db.update("INSERT INTO ai_chapter(user_id,book_id,chapter_id,job_id,state,phase,started_at,source_count) VALUES (?,?,?,?,'running','queued',now(),?) ON CONFLICT(user_id,book_id,chapter_id) DO UPDATE SET job_id=EXCLUDED.job_id,state='running',phase='queued',started_at=now(),source_count=EXCLUDED.source_count,output_chars=0,summary='',drafts='[]',error='',client_snapshot=NULL,client_deadline=NULL,updated_at=now()",user,book,chapter,job,snap.notes().size());
                workers.submit(() -> run(user,snap,connection,prompt,job));
            } catch(RuntimeException e) { capacity.release(); fail(user,book,chapter,job,"任务未能启动，请重试"); throw e; }
            return state(user,book,chapter);
        });
    }
    private void run(UUID user,Snapshot snap,Connection connection,String prompt,UUID job) {
        try {
            progress(user,snap,job,"requesting","");
            long[] lastWrite={0};
            String text=transport.generateStreaming(connection,AiOrganizer.SYSTEM,prompt,16000,new AiStream.Listener() {
                boolean hasText;
                public void phase(String phase) { if(!hasText) progress(user,snap,job,phase,""); }
                public void accept(String partial) {
                    hasText=true;
                    long now=System.nanoTime();
                    if(now-lastWrite[0]>60_000_000L) { progress(user,snap,job,"receiving",partial); lastWrite[0]=now; }
                }
            });
            progress(user,snap,job,"validating",text);
            complete(user,snap,job,text);
        } catch(Exception e) { fail(user,snap.book().bookId(),snap.chapter().id(),job,e instanceof ApiException?e.getMessage():"整理失败，旧结果未改动，请重试"); }
        finally { capacity.release(); }
    }
    private void complete(UUID user,Snapshot snap,UUID job,String text) {
        var groups=organizer.validate(text,snap);
        notebook.serialized(() -> tx.execute(status -> {
                int current=db.queryForObject("SELECT count(*) FROM ai_chapter WHERE user_id=? AND book_id=? AND chapter_id=? AND job_id=? AND state='running'",Integer.class,user,snap.book().bookId(),snap.chapter().id(),job);
                if(current!=1) return null;
                // Sent/uncertain results remain immutable history, including independent notes sent through the original view.
                for(var card:cards(user,snap.book().bookId(),snap.chapter().id())) if(card.status().equals("ready")) db.update("DELETE FROM ai_note WHERE user_id=? AND id=?",user,card.id());
                int position=0;
                for(var group:groups) db.update("INSERT INTO ai_note(user_id,id,book_id,chapter_id,position,title,body,snapshot) VALUES (?,?,?,?,?,?,?,?)",user,"ai:"+UUID.randomUUID(),snap.book().bookId(),snap.chapter().id(),position++,group.title(),group.body(),json(group.snapshot()));
                db.update("UPDATE ai_chapter SET state='ready',phase='complete',drafts='[]',error='',client_snapshot=NULL,client_deadline=NULL,updated_at=now() WHERE user_id=? AND book_id=? AND chapter_id=? AND job_id=?",user,snap.book().bookId(),snap.chapter().id(),job);
                return null;
            }));
    }
    public ChapterState browserResult(UUID user,BrowserResult result,boolean partial) {
        return notebook.exclusive(() -> {
            expireBrowserJobs(user);
            if(result.jobId()==null || result.text()!=null && result.text().length()>200000) throw new ApiException(400,"本地整理结果无效或过长");
            var rows=db.queryForList("SELECT state,client_snapshot FROM ai_chapter WHERE user_id=? AND book_id=? AND chapter_id=? AND job_id=?",user,result.bookId(),result.chapterId(),result.jobId());
            if(rows.isEmpty()) throw new ApiException(409,"整理任务已失效，请刷新");
            var row=rows.getFirst();
            if("ready".equals(row.get("state"))) return state(user,result.bookId(),result.chapterId()); // Lost acknowledgement: no duplicate cards.
            if(!"running".equals(row.get("state")) || row.get("client_snapshot")==null) throw new ApiException(409,"本地整理任务已结束，请刷新");
            var snap=snapshot((String)row.get("client_snapshot"));
            if(result.failed()) fail(user,result.bookId(),result.chapterId(),result.jobId(),"本地模型连接失败或已取消，旧结果未改动，请重试");
            else if(partial) progress(user,snap,result.jobId(),"receiving",result.text()==null?"":result.text());
            else {
                try { complete(user,snap,result.jobId(),result.text()==null?"":result.text()); }
                catch(ApiException e) { fail(user,result.bookId(),result.chapterId(),result.jobId(),e.getMessage()); throw e; }
            }
            return state(user,result.bookId(),result.chapterId());
        });
    }
    static String summary(String partial) {
        var match=java.util.regex.Pattern.compile("(?s)^\\s*(?:```json\\s*)?\\{\\s*\"summary\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").matcher(partial);
        if(!match.find()) return "";
        try { String value=new ObjectMapper().readValue(match.group(1),String.class).strip(); return value.length()<=120?value:value.substring(0,120)+"…"; }
        catch(Exception e) { return ""; }
    }
    private void progress(UUID user,Snapshot snap,UUID job,String phase,String partial) {
        db.update("UPDATE ai_chapter SET phase=?,output_chars=?,drafts=?,updated_at=now() WHERE user_id=? AND book_id=? AND chapter_id=? AND job_id=? AND state='running'",phase,partial.length(),json(AiDrafts.read(partial,snap)),user,snap.book().bookId(),snap.chapter().id(),job);
    }
    private void fail(UUID user,String book,String chapter,UUID job,String message) { db.update("UPDATE ai_chapter SET state='failed',drafts='[]',client_snapshot=NULL,client_deadline=NULL,error=?,updated_at=now() WHERE user_id=? AND book_id=? AND chapter_id=? AND job_id=? AND state='running'",message,user,book,chapter,job); }
    private Card find(UUID user,String book,String chapter,String id) { return cards(user,book,chapter).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> new ApiException(404,"整理结果不存在，请刷新")); }
    public ChapterState update(UUID user,Update change) {
        return notebook.exclusive(() -> {
            editable(user,change.bookId(),change.chapterId());
            var card=find(user,change.bookId(),change.chapterId(),change.id());
            if(!card.status().equals("ready")) throw new ApiException(409,"已发送或需核对的结果不能修改");
            if(card.snapshot().notes().size()==1) throw new ApiException(400,"独立摘录保留原文，不支持改写");
            if(change.title()==null || change.title().isBlank() || change.title().length()>120 || change.body()==null || change.body().isBlank() || change.body().length()>20000) throw new ApiException(400,"请填写标题和正文（标题最多 120 字，正文最多 20000 字）");
            if(db.update("UPDATE ai_note SET title=?,body=?,edited=true,version=version+1 WHERE user_id=? AND id=? AND version=?",change.title().strip(),change.body().strip(),user,change.id(),change.version())!=1) throw new ApiException(409,"这条结果已在其他页面修改，请刷新后再编辑");
            return state(user,change.bookId(),change.chapterId());
        });
    }
    public Map<String,Object> send(UUID user,Selection selection) {
        return notebook.exclusive(() -> {
            editable(user,selection.bookId(),selection.chapterId());
            if(!selection.confirmed() || selection.ids()==null || selection.ids().isEmpty() || selection.ids().size()>100 || new HashSet<>(selection.ids()).size()!=selection.ids().size()) throw new ApiException(400,"请选择 1 到 100 条结果并确认发送");
            if(!Boolean.TRUE.equals(remote.status(user).get("sendingEnabled"))) throw new ApiException(400,"请先配置 flomo API 地址");
            var selected=selection.ids().stream().map(id -> find(user,selection.bookId(),selection.chapterId(),id)).toList();
            for(var card:selected) {
                if(!card.status().equals("ready")) throw new ApiException(409,"结果已同步或需核对，请刷新");
                for(var note:card.snapshot().notes()) if(Set.of("uncertain","sending").contains(ledger.status(user,note))) throw new ApiException(409,"来源存在需核对的发送，请先处理");
            }
            String error="";
            for(var card:selected) {
                ledger.reserveOrganized(user,card.id(),AiOrganizer.content(card),card.snapshot().notes());
                try { remote.send(user,AiOrganizer.content(card)); }
                catch(RuntimeException e) { ledger.finish(user,card.id(),"uncertain"); error="发送结果不确定，已停止，请到 flomo 核对"; break; }
                ledger.finish(user,card.id(),"sent");
            }
            return Map.of("chapter",state(user,selection.bookId(),selection.chapterId()),"error",error);
        });
    }
    public ChapterState resolve(UUID user,Resolution request) {
        return notebook.exclusive(() -> {
            if(request.delivered()==null) throw new ApiException(400,"请选择核对结果");
            editable(user,request.bookId(),request.chapterId());
            var card=find(user,request.bookId(),request.chapterId(),request.id());
            if(card.snapshot().notes().size()==1 && ledger.ownStatus(user,card.id()).equals("ready")) ledger.resolveUncertain(user,card.snapshot().notes().getFirst(),request.delivered());
            else ledger.resolveOrganized(user,card.id(),request.delivered());
            return state(user,request.bookId(),request.chapterId());
        });
    }
    @PreDestroy void stop() { workers.shutdownNow(); }
}
