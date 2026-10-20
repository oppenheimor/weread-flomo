package cn.sparkden.weread;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static cn.sparkden.weread.Models.*;

@Component
public class Normalizer {
    private final ObjectMapper mapper;
    public Normalizer(ObjectMapper mapper) { this.mapper = mapper; }
    private String key(String book, String type, String id) {
        try { return mapper.writeValueAsString(List.of(book,type,id)); }
        catch (Exception e) { throw new IllegalStateException("无法生成来源键"); }
    }
    /** 从来源键取书籍 ID；旧版或迁移数据中无法识别的键返回 null。 */
    public String bookOf(String sourceKey) {
        try {
            var parts = mapper.readTree(sourceKey);
            return parts.isArray() && parts.size()==3 && parts.get(0).isTextual() ? parts.get(0).asText() : null;
        } catch (Exception e) { return null; }
    }
    public static String tag(String title) {
        String clean = title.replaceAll("(?U)\\s+", "＿").replace('#','＃').replace('/','／').replaceAll("^《|》$", "");
        return "#微信读书/《" + clean + "》";
    }
    /** flomo 使用 Markdown 加粗标题，分隔线使用普通文本，避免横线被过滤。 */
    public static String memoBody(String quote, String thought) {
        String original = quote.isEmpty() ? "" : "**原文：**\n\n" + quote;
        String annotation = thought.isEmpty() ? "" : "**想法：**\n\n" + thought;
        return original + (original.isEmpty() || annotation.isEmpty() ? "" : "\n\n────────\n\n") + annotation;
    }
    private static long position(String range) {
        try { return Long.parseLong(range.split("-", 2)[0]); }
        catch (NumberFormatException e) { return Long.MAX_VALUE; }
    }
    public Normalized normalize(Book book, SourceNotes source) {
        var highlights = new LinkedHashMap<String,Highlight>();
        var reviews = new LinkedHashMap<String,Review>();
        source.highlights().forEach(h -> highlights.put(h.id(),h));
        source.reviews().forEach(r -> reviews.put(r.id(),r));
        var attached = new HashMap<String,List<Review>>();
        var independent = new ArrayList<Review>();
        for (Review r : reviews.values()) {
            if (r.content().isBlank()) continue; // 不生成空 memo，也不将空想法计入已发送来源。
            var matches = highlights.values().stream().filter(h -> !r.chapter().isBlank() && !r.range().isBlank()
                && h.chapter().equals(r.chapter()) && h.range().equals(r.range())).toList();
            if (matches.size()==1) attached.computeIfAbsent(matches.getFirst().id(), k -> new ArrayList<>()).add(r);
            else independent.add(r);
        }
        var chapters = new HashMap<String,Chapter>();
        source.chapters().forEach(c -> chapters.put(c.id(), c));
        var other = new Chapter("", "其他笔记", Integer.MAX_VALUE);
        var positions = new HashMap<String,Long>();
        var notes = new ArrayList<Note>();
        String prefix = tag(book.title()) + "\n\n";
        for (Highlight h : highlights.values()) {
            if (h.text().isBlank()) throw new ApiException(502,"划线原文为空，已停止生成预览");
            String id = key(book.bookId(),"highlight",h.id());
            var keys = new ArrayList<String>(); keys.add(id);
            var thoughts = attached.getOrDefault(h.id(),List.of());
            thoughts.forEach(r -> keys.add(key(book.bookId(),"review",r.id())));
            String quote = h.text().strip(), thought = String.join("\n\n",thoughts.stream().map(r -> r.content().strip()).toList());
            String content = prefix + memoBody(quote, thought);
            notes.add(new Note(id,book.bookId(),"highlight",keys,content,quote,thought,chapters.getOrDefault(h.chapter(),other),h.createTime()));
            positions.put(id, position(h.range()));
        }
        for (Review r : independent) {
            String id = key(book.bookId(),"review",r.id());
            String quote = r.abstractText().strip(), thought = r.content().strip();
            notes.add(new Note(id,book.bookId(),"review",List.of(id),prefix+memoBody(quote,thought),quote,thought,chapters.getOrDefault(r.chapter(),other),r.createTime()));
            positions.put(id, position(r.range()));
        }
        notes.sort(Comparator.comparingInt((Note n) -> n.chapter().order())
            .thenComparing(n -> n.chapter().id()).thenComparingLong(n -> positions.get(n.id())));
        var warnings = new ArrayList<String>();
        if (book.noteCount()!=null && book.noteCount()!=highlights.size()) warnings.add("划线数量与书籍概览不一致，请核对");
        if (book.reviewCount()!=null && book.reviewCount()!=reviews.size()) warnings.add("想法数量与书籍概览不一致，请核对");
        return new Normalized(notes,warnings);
    }
}
