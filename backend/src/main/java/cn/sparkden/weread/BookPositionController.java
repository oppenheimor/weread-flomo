package cn.sparkden.weread;

import java.security.Principal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book-position")
public class BookPositionController {
    private final JdbcTemplate db;
    private final Accounts accounts;
    public BookPositionController(JdbcTemplate db, Accounts accounts) { this.db=db; this.accounts=accounts; }
    private void validate(String value) {
        if(value==null || value.isBlank() || value.length()>500) throw new ApiException(400,"书籍或章节 ID 无效");
    }
    @GetMapping public Map<String,String> get(Principal principal,@RequestParam String bookId) {
        validate(bookId);
        var rows=db.queryForList("SELECT chapter_id FROM book_position WHERE user_id=? AND book_id=?",String.class,accounts.id(principal.getName()),bookId);
        return Map.of("chapterId",rows.isEmpty()?"":rows.getFirst());
    }
    public record Position(String bookId,String chapterId) {}
    @PostMapping public Map<String,Boolean> save(Principal principal,@RequestBody Position body) {
        validate(body.bookId()); validate(body.chapterId());
        db.update("INSERT INTO book_position(user_id,book_id,chapter_id) VALUES (?,?,?) ON CONFLICT(user_id,book_id) DO UPDATE SET chapter_id=excluded.chapter_id,updated_at=now()",accounts.id(principal.getName()),body.bookId(),body.chapterId());
        return Map.of("ok",true);
    }
}
