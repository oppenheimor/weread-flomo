package cn.sparkden.weread;

import java.util.*;
import static cn.sparkden.weread.Models.*;

public final class AiModels {
    private AiModels() {}
    public record Provider(String id,String name,String protocol,String baseUrl,boolean hasKey,List<String> models) {}
    public record ProviderChange(String id,String name,String protocol,String baseUrl,String apiKey,List<String> models) {
        @Override public String toString() { return "ProviderChange[REDACTED]"; }
    }
    public record DefaultModel(String providerId,String model) {}
    public record Settings(List<Provider> providers,DefaultModel defaultModel,boolean thinkingControl) {}
    public record Connection(String protocol,String baseUrl,String key,String model,boolean deepThinking) {
        public Connection(String protocol,String baseUrl,String key,String model) { this(protocol,baseUrl,key,model,false); }
        public Connection withThinking(boolean enabled) { return new Connection(protocol,baseUrl,key,model,enabled); }
        @Override public String toString() { return "Connection[REDACTED]"; }
    }
    public record Snapshot(Book book,Chapter chapter,List<Note> notes) {}
    public record Card(String id,String title,String body,Snapshot snapshot,String status,boolean edited,int version) {}
    public record Progress(String phase,Long startedAt,int sourceCount,int outputChars,String summary) {}
    public record Draft(String id,String kind,String title,String body,String quote,String thought) {}
    public record ChapterState(String state,String error,List<Card> notes,Progress progress,List<Draft> drafts) {}
    public record Start(String previewId,String chapterId,boolean replaceConfirmed,boolean deepThinking) {
        public Start(String previewId,String chapterId,boolean replaceConfirmed) { this(previewId,chapterId,replaceConfirmed,false); }
    }
    public record BrowserJob(String mode,UUID jobId,String bookId,String chapterId,String baseUrl,String model,String system,String prompt,ChapterState chapter) {}
    public record BrowserResult(String bookId,String chapterId,UUID jobId,String text,boolean failed) {}
    public record Selection(String bookId,String chapterId,List<String> ids,boolean confirmed) {}
    public record Update(String bookId,String chapterId,String id,String title,String body,int version) {}
    public record Resolution(String bookId,String chapterId,String id,Boolean delivered) {}
}
