package cn.sparkden.weread;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static cn.sparkden.weread.Models.*;

/** Capability-based, read-only sharing. Never consults previews, AI, or the delivery ledger. */
@Service
public class ShareService {
    public record State(boolean enabled, String token) {}
    record Grant(UUID owner, String bookId) {}
    public record PublicNote(String id, String kind, String quote, String thought, Chapter chapter, Long createTime) {}
    public record PublicBook(Book book, List<PublicNote> notes) {}
    public record View(String scope, List<Book> books, PublicBook detail, String username) {}
    private final JdbcTemplate db;
    private final RemoteApis remote;
    private final Normalizer normalizer;
    private final SecureRandom random = new SecureRandom();
    // Bound anonymous upstream work without taking the private notebook/send lock.
    private final Semaphore upstream = new Semaphore(4);
    private static final class Budget {
        long started = System.currentTimeMillis(); int requests; int active;
    }
    private final Map<UUID, Budget> budgets = new HashMap<>();
    public ShareService(JdbcTemplate db, RemoteApis remote, Normalizer normalizer) {
        this.db=db; this.remote=remote; this.normalizer=normalizer;
    }
    static String scope(String bookId) {
        if (bookId==null) return "";
        if (bookId.isBlank() || bookId.length()>100 || bookId.chars().anyMatch(Character::isISOControl))
            throw new ApiException(400,"书籍 ID 无效");
        return bookId;
    }
    public State state(UUID owner, String bookId) {
        var tokens=db.queryForList("SELECT token FROM public_share WHERE user_id=? AND book_id=?",String.class,owner,scope(bookId));
        return tokens.isEmpty() ? new State(false,null) : new State(true,tokens.getFirst());
    }
    public State enable(UUID owner, String bookId) {
        String book=scope(bookId);
        // Credentials are required only to create a new link, not to inspect or revoke one.
        if (!Boolean.TRUE.equals(remote.status(owner).get("wereadConfigured"))) throw new ApiException(400,"请先连接微信读书");
        if (!book.isEmpty() && remote.books(owner).stream().noneMatch(b -> b.bookId().equals(book))) throw missing();
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Atomic/idempotent under concurrent opens: do not silently invalidate a live link.
        db.update("INSERT INTO public_share(user_id,book_id,token) VALUES (?,?,?) ON CONFLICT (user_id,book_id) DO NOTHING",owner,book,token);
        return state(owner,bookId);
    }
    public State disable(UUID owner, String bookId) {
        db.update("DELETE FROM public_share WHERE user_id=? AND book_id=?",owner,scope(bookId));
        return new State(false,null);
    }
    static ApiException missing() { return new ApiException(404,"分享已关闭或不存在"); }
    Grant grant(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw missing();
        var grants=db.query("SELECT user_id,book_id FROM public_share WHERE token=?",(rs,n) -> new Grant(rs.getObject(1,UUID.class),rs.getString(2)),token);
        if(grants.isEmpty()) throw missing();
        return grants.getFirst();
    }
    public boolean exists(String token) {
        try { grant(token); return true; } catch(ApiException e) { return false; }
    }
    private <T> T bounded(UUID owner, Supplier<T> work) {
        synchronized(budgets) {
            long now=System.currentTimeMillis();
            budgets.entrySet().removeIf(e -> e.getValue().active==0 && now-e.getValue().started>=60000);
            if(!budgets.containsKey(owner) && budgets.size()>=1000) throw new ApiException(429,"访问较频繁，请稍后重试");
            Budget budget=budgets.computeIfAbsent(owner,key -> new Budget());
            if(budget.requests>=60 || budget.active>=2) throw new ApiException(429,"访问较频繁，请稍后重试");
            budget.requests++; budget.active++;
        }
        boolean acquired=upstream.tryAcquire();
        try {
            if(!acquired) throw new ApiException(429,"访问较频繁，请稍后重试");
            return work.get();
        } finally {
            if(acquired) upstream.release();
            synchronized(budgets) { budgets.get(owner).active--; }
        }
    }
    public View view(String token, String requestedBook) {
        Grant grant=grant(token);
        String selected=requestedBook==null ? grant.bookId() : scope(requestedBook);
        // A single-book capability must never enumerate or read another book.
        if(!grant.bookId().isEmpty() && !grant.bookId().equals(selected)) throw missing();
        return bounded(grant.owner(),() -> {
            View result;
            try {
                List<Book> books=remote.books(grant.owner());
                if(selected.isEmpty()) result=new View("shelf",books,null,
                    db.queryForObject("SELECT username FROM app_user WHERE id=?",String.class,grant.owner()));
                else {
                    Book book=books.stream().filter(b -> b.bookId().equals(selected)).findFirst().orElseThrow(ShareService::missing);
                    var normalized=normalizer.normalize(book,remote.notes(grant.owner(),selected));
                    var notes=normalized.notes().stream().map(n -> new PublicNote(n.id(),n.kind(),n.quote(),n.thought(),n.chapter(),n.createTime())).toList();
                    result=new View(grant.bookId().isEmpty()?"shelf":"book",List.of(),new PublicBook(book,notes),null);
                }
            } catch(ApiException e) {
                if(e.status()==404) throw e;
                throw new ApiException(503,"暂时无法加载，请稍后再试");
            } catch(RuntimeException e) {
                throw new ApiException(503,"暂时无法加载，请稍后再试");
            }
            // Closing while a slow upstream read is in flight must not return its contents.
            grant(token);
            return result;
        });
    }
}
