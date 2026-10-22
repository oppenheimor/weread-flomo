package cn.sparkden.weread;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatisticsController {
    private final Accounts accounts;
    private final JdbcTemplate db;
    private final Normalizer normalizer;
    public StatisticsController(Accounts accounts, JdbcTemplate db, Normalizer normalizer) {
        this.accounts=accounts; this.db=db; this.normalizer=normalizer;
    }
    public record Statistics(long notes, int books, int activeDays, String today, Map<String,Long> days) {}
    @GetMapping("/api/statistics")
    public Statistics statistics(Principal principal) { return forUser(accounts.id(principal.getName())); }

    public Statistics forUser(UUID user) {
        // One snapshot, one row per original source. Organized deliveries without new sources add nothing.
        var days=new TreeMap<String,Long>();
        var books=new HashSet<String>();
        db.query("""
            SELECT s.source_key, (d.updated_at AT TIME ZONE 'Asia/Shanghai')::date AS day
            FROM delivery_source s JOIN delivery d ON d.user_id=s.user_id AND d.note_id=s.note_id
            WHERE s.user_id=? AND d.status='sent'
            """, row -> {
                days.merge(row.getString("day"),1L,Long::sum);
                String book=normalizer.bookOf(row.getString("source_key"));
                if(book!=null) books.add(book);
            }, user);
        return new Statistics(days.values().stream().mapToLong(Long::longValue).sum(),books.size(),days.size(),
            LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),days);
    }
}
