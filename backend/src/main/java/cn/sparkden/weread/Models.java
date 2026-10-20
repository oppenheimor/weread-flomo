package cn.sparkden.weread;

import java.util.*;

public final class Models {
    private Models() {}
    public record Book(String bookId, String title, String author, Integer noteCount, Integer reviewCount,
            String cover, Double readingProgress, Integer markedStatus) {
        public Book(String bookId, String title, String author, Integer noteCount, Integer reviewCount) {
            this(bookId, title, author, noteCount, reviewCount, "", null, null);
        }
    }
    public record Highlight(String id, String text, String chapter, String range, Long createTime) {
        public Highlight(String id, String text, String chapter, String range) { this(id, text, chapter, range, null); }
    }
    public record Review(String id, String content, String abstractText, String chapter, String range, Long createTime) {
        public Review(String id, String content, String abstractText, String chapter, String range) { this(id, content, abstractText, chapter, range, null); }
    }
    public record Chapter(String id, String title, int order) {}
    public record SourceNotes(List<Highlight> highlights, List<Review> reviews, List<Chapter> chapters) {
        public SourceNotes(List<Highlight> highlights, List<Review> reviews) { this(highlights, reviews, List.of()); }
    }
    /** content 为实际发送正文；quote/thought 仅供预览分块展示，与 content 同源生成。 */
    public record Note(String id, String bookId, String kind, List<String> sourceKeys, String content, String quote, String thought, Chapter chapter, Long createTime) {
        public Note(String id, String bookId, String kind, List<String> sourceKeys, String content) { this(id, bookId, kind, sourceKeys, content, "", "", new Chapter("", "其他笔记", Integer.MAX_VALUE), null); }
    }
    public record PreviewNote(String id, String bookId, String kind, String content, String quote, String thought, int sources, String status, Chapter chapter, Long createTime) {}
    public record Normalized(List<Note> notes, List<String> warnings) {}
    public record SendRequest(String previewId, List<String> ids, Boolean confirmed) {}
    public record SendResult(List<PreviewNote> notes, String deliveryError) {}
    public record SyncStats(int synced, int uncertain, int deleted) {}
    public record ThoughtRequest(String previewId, String id, String thought) {}
    public record DeleteRequest(String previewId, String id, Boolean confirmed) {}
    public record ResolveRequest(String previewId, String id, Boolean delivered) {}
    public record PreviewResponse(String previewId, Book book, List<String> warnings, List<PreviewNote> notes) {}
}
