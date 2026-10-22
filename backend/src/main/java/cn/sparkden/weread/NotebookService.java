package cn.sparkden.weread;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import static cn.sparkden.weread.Models.*;

@Service
public class NotebookService {
    private record Preview(UUID owner, Book book, Normalized data, Instant expires) {}
    private final RemoteApis remote;
    private final Normalizer normalizer;
    private final Ledger ledger;
    private final Credentials credentials;
    private final Map<UUID,Map<String,Book>> books=new HashMap<>();
    private final Map<String,Preview> previews=new LinkedHashMap<>();
    // 凭证变更、读取及发送共用任务锁，避免拉取中换账号或批次中途换目的地。
    private final ReentrantLock operation=new ReentrantLock();
    public NotebookService(RemoteApis remote,Normalizer normalizer,Ledger ledger,Credentials credentials) {
        this.remote=remote; this.normalizer=normalizer; this.ledger=ledger; this.credentials=credentials;
    }
    <T> T serialized(Supplier<T> work) {
        operation.lock();
        try { return work.get(); } finally { operation.unlock(); }
    }
    <T> T exclusive(Supplier<T> work) {
        if(!operation.tryLock()) throw new ApiException(409,"已有任务正在执行，请结束后再操作或修改凭证");
        try { return work.get(); } finally { operation.unlock(); }
    }
    public Map<String,Object> saveCredentials(UUID user, Credentials.Change change) {
        return exclusive(() -> { credentials.save(user,change); invalidate(user); return credentials.status(user); });
    }
    public Credentials.Revealed revealCredential(UUID user, String provider) {
        return exclusive(() -> credentials.reveal(user, provider));
    }
    public Map<String,Object> deleteCredentials(UUID user,String provider) {
        return exclusive(() -> { credentials.delete(user,provider); invalidate(user); return credentials.status(user); });
    }
    private void invalidate(UUID user) {
        books.remove(user); previews.values().removeIf(p -> p.owner().equals(user));
    }
    public List<Book> books(UUID user) {
        return exclusive(() -> {
            var result=remote.books(user); var index=new HashMap<String,Book>(); result.forEach(b -> index.put(b.bookId(),b)); books.put(user,index); return result;
        });
    }
    /** 按书统计账本中已发送/结果不确定的来源条数（每条划线、每条想法各计一次）。 */
    public Map<String,SyncStats> syncStats(UUID user) {
        var result=new HashMap<String,int[]>();
        var dismissed=ledger.dismissedSources(user);
        ledger.sourceStatuses(user).forEach((key,status) -> {
            if(dismissed.contains(key)) return;
            String book=normalizer.bookOf(key); if(book==null) return;
            result.computeIfAbsent(book,k -> new int[3])[status.equals("sent")?0:1]++;
        });
        for(String key:dismissed) {
            String book=normalizer.bookOf(key); if(book!=null) result.computeIfAbsent(book,k -> new int[3])[2]++;
        }
        var stats=new HashMap<String,SyncStats>();
        result.forEach((book,c) -> stats.put(book,new SyncStats(c[0],c[1],c[2])));
        return stats;
    }
    private Note withThought(Note note,Map<String,String> thoughts) {
        if(!thoughts.containsKey(note.id())) return note;
        String thought=thoughts.get(note.id());
        String prefix=note.content().substring(0,note.content().indexOf("\n\n")+2);
        String content=prefix+Normalizer.memoBody(note.quote(),thought);
        return new Note(note.id(),note.bookId(),note.kind(),note.sourceKeys(),content,note.quote(),thought,note.chapter(),note.createTime());
    }
    private List<PreviewNote> statuses(UUID user,List<Note> notes) {
        var dismissed=ledger.dismissedSources(user);
        var thoughts=ledger.thoughts(user);
        return notes.stream().filter(n -> Collections.disjoint(n.sourceKeys(),dismissed)).map(n -> withThought(n,thoughts)).map(n -> new PreviewNote(n.id(),n.bookId(),n.kind(),n.content(),n.quote(),n.thought(),n.sourceKeys().size(),ledger.status(user,n),n.chapter(),n.createTime())).toList();
    }
    public PreviewResponse preview(UUID user,String bookId) {
        return exclusive(() -> {
            Book book=books.getOrDefault(user,Map.of()).get(bookId);
            if(book==null) throw new ApiException(400,"请先加载书籍并选择一本");
            var data=normalizer.normalize(book,remote.notes(user,bookId)); String id=UUID.randomUUID().toString();
            previews.values().removeIf(p -> p.expires().isBefore(Instant.now()));
            if(previews.size()>=10) previews.remove(previews.keySet().iterator().next());
            previews.put(id,new Preview(user,book,data,Instant.now().plusSeconds(1800)));
            return new PreviewResponse(id,book,data.warnings(),statuses(user,data.notes()));
        });
    }
    public AiModels.Snapshot aiSnapshot(UUID user,String previewId,String chapterId) {
        return exclusive(() -> {
            Preview preview=previews.get(previewId);
            if(preview==null || !preview.owner().equals(user) || preview.expires().isBefore(Instant.now())) throw new ApiException(409,"预览已过期，请重新拉取");
            var thoughts=ledger.thoughts(user); var dismissed=ledger.dismissedSources(user);
            var ready=preview.data().notes().stream().filter(n -> n.chapter().id().equals(chapterId) && Collections.disjoint(n.sourceKeys(),dismissed) && ledger.status(user,n).equals("ready")).map(n -> withThought(n,thoughts)).toList();
            if(ready.isEmpty()) throw new ApiException(400,"本章没有可重新整理的笔记");
            return new AiModels.Snapshot(preview.book(),ready.getFirst().chapter(),ready);
        });
    }
    public List<PreviewNote> refreshPreview(UUID user,String previewId) {
        return exclusive(() -> {
            var preview=previews.get(previewId);
            if(preview==null || !preview.owner().equals(user)) throw new ApiException(409,"预览已过期，请重新拉取");
            return statuses(user,preview.data().notes());
        });
    }
    public SendResult send(UUID user,SendRequest request) {
        return exclusive(() -> sendLocked(user,request));
    }
    public List<PreviewNote> saveThought(UUID user,ThoughtRequest request) {
        return exclusive(() -> {
            if(request.thought()==null || request.thought().length()>10000) throw new ApiException(400,"想法最多 10000 字");
            Preview preview=previews.get(request.previewId());
            if(preview==null || !preview.owner().equals(user) || preview.expires().isBefore(Instant.now())) throw new ApiException(409,"预览已过期，请重新拉取");
            Note note=preview.data().notes().stream().filter(n -> n.id().equals(request.id())).findFirst().orElseThrow(() -> new ApiException(400,"笔记不属于本次预览"));
            if(!Collections.disjoint(note.sourceKeys(),ledger.dismissedSources(user))) throw new ApiException(409,"笔记已删除，请重新拉取");
            String thought=request.thought().strip();
            if(note.quote().isBlank() && thought.isEmpty()) throw new ApiException(400,"这条笔记没有划线原文，请保留想法或删除笔记");
            ledger.saveThought(user,note.id(),thought);
            return statuses(user,preview.data().notes());
        });
    }
    public List<PreviewNote> delete(UUID user,DeleteRequest request) {
        return exclusive(() -> {
            if(!Boolean.TRUE.equals(request.confirmed())) throw new ApiException(400,"请确认删除");
            Preview preview=previews.get(request.previewId());
            if(preview==null || !preview.owner().equals(user) || preview.expires().isBefore(Instant.now())) throw new ApiException(409,"预览已过期，请重新拉取");
            Note note=preview.data().notes().stream().filter(n -> n.id().equals(request.id())).findFirst().orElseThrow(() -> new ApiException(400,"笔记不属于本次预览"));
            ledger.dismiss(user,note);
            return statuses(user,preview.data().notes());
        });
    }
    public List<PreviewNote> resolve(UUID user,ResolveRequest request) {
        return exclusive(() -> {
            if(request.delivered()==null) throw new ApiException(400,"请选择核对结果");
            Preview preview=previews.get(request.previewId());
            if(preview==null || !preview.owner().equals(user) || preview.expires().isBefore(Instant.now())) throw new ApiException(409,"预览已过期，请重新拉取");
            Note note=preview.data().notes().stream().filter(n -> n.id().equals(request.id())).findFirst().orElseThrow(() -> new ApiException(400,"笔记不属于本次预览"));
            ledger.resolveUncertain(user,note,request.delivered());
            return statuses(user,preview.data().notes());
        });
    }
    private SendResult sendLocked(UUID user,SendRequest request) {
        if(!Boolean.TRUE.equals(remote.status(user).get("sendingEnabled"))) throw new ApiException(400,"请在账号设置中配置 flomo API 地址");
        if(!Boolean.TRUE.equals(request.confirmed())) throw new ApiException(400,"请确认发送");
        var ids=request.ids();
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size()) throw new ApiException(400,"请选择 1 到 100 条不重复的笔记");
        Preview preview=previews.get(request.previewId());
        if(preview==null || !preview.owner().equals(user) || preview.expires().isBefore(Instant.now())) throw new ApiException(409,"预览已过期，请重新拉取");
        var index=new HashMap<String,Note>(); preview.data().notes().forEach(n -> index.put(n.id(),n));
        var thoughts=ledger.thoughts(user);
        var selected=ids.stream().map(id -> { var n=index.get(id); if(n==null) throw new ApiException(400,"笔记不属于本次预览"); return withThought(n,thoughts); }).toList();
        var dismissed=ledger.dismissedSources(user);
        for(Note n:selected) if(!Collections.disjoint(n.sourceKeys(),dismissed)) throw new ApiException(409,"笔记已删除，请重新拉取");
        for(Note n:selected) if(!ledger.status(user,n).equals("ready")) throw new ApiException(409,"笔记已发送或结果不确定，请重新拉取");
        String error=null;
        for(Note n:selected) {
            ledger.reserve(user,n);
            try { remote.send(user,n.content()); }
            catch(RuntimeException e) {
                ledger.finish(user,n.id(),"uncertain");
                error=e instanceof ApiException?e.getMessage():"发送结果不确定，请到 flomo 核对"; break;
            }
            ledger.finish(user,n.id(),"sent");
        }
        return new SendResult(statuses(user,preview.data().notes()),error);
    }
}
