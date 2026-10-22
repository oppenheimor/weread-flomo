package cn.sparkden.weread;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static cn.sparkden.weread.Models.*;

class CoreTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final Book book=new Book("b","测试书","作者",1,1);
    @Test void memoHeadingsOmitEmptySections() {
        assertEquals("**原文：**\n\n原文", Normalizer.memoBody("原文", ""));
        assertEquals("**想法：**\n\n批注", Normalizer.memoBody("", "批注"));
        assertEquals("", Normalizer.memoBody("", ""));
    }
    @Test void attachesThoughtOnlyByChapterAndRange() {
        var normalizer=new Normalizer(mapper);
        var source=new SourceNotes(List.of(new Highlight("h","原文","1","1-3")),List.of(new Review("r","想法","","1","1-3")));
        var result=normalizer.normalize(book,source);
        assertEquals(1,result.notes().size());
        assertEquals("#微信读书/《测试书》\n\n**原文：**\n\n原文\n\n────────\n\n**想法：**\n\n想法",result.notes().getFirst().content());
        assertEquals(2,result.notes().getFirst().sourceKeys().size());
        assertEquals("原文",result.notes().getFirst().quote()); assertEquals("想法",result.notes().getFirst().thought());
        assertTrue(result.warnings().isEmpty());
    }
    @Test void preservesSourceDatesWithoutChangingSentContent() {
        var normalizer = new Normalizer(mapper);
        var source = new SourceNotes(
            List.of(new Highlight("h", "原文", "1", "1-3", 1748563200L)),
            List.of(new Review("attached", "想法", "", "1", "1-3", 1748649600L),
                new Review("independent", "书评", "", "", "", 1748736000L)));
        var notes = normalizer.normalize(book, source).notes();
        var highlight = notes.stream().filter(n -> n.kind().equals("highlight")).findFirst().orElseThrow();
        var review = notes.stream().filter(n -> n.kind().equals("review")).findFirst().orElseThrow();
        assertEquals(1748563200L, highlight.createTime());
        assertEquals(1748736000L, review.createTime());
        assertEquals("#微信读书/《测试书》\n\n**原文：**\n\n原文\n\n────────\n\n**想法：**\n\n想法", highlight.content());
        assertNull(normalizer.normalize(book, new SourceNotes(List.of(new Highlight("old", "原文", "", "")), List.of())).notes().getFirst().createTime());
    }
    @Test void chaptersAreSortedAndThoughtsStayAttached() {
        var source = new SourceNotes(List.of(
            new Highlight("late", "第二章", "c2", "10-20"),
            new Highlight("later", "后段", "c1", "100-120"),
            new Highlight("early", "前段", "c1", "20-30")),
            List.of(new Review("r", "想法", "", "c1", "20-30")),
            List.of(new Chapter("c2", "第二章", 2), new Chapter("c1", "第一章", 1)));
        var notes = new Normalizer(mapper).normalize(book, source).notes();
        assertEquals(List.of("前段", "后段", "第二章"), notes.stream().map(Note::quote).toList());
        assertEquals("第一章", notes.getFirst().chapter().title());
        assertEquals("想法", notes.getFirst().thought());
        assertEquals(2, notes.getFirst().sourceKeys().size());
    }
    @Test void unknownChaptersAndReviewsAreKeptAtEnd() {
        var source = new SourceNotes(List.of(new Highlight("h", "未知章节", "missing", "bad"),
            new Highlight("known", "原文", "c", "1-2")),
            List.of(new Review("r", "书评", "", "", "")), List.of(new Chapter("c", "章节", 1)));
        var notes = new Normalizer(mapper).normalize(book, source).notes();
        assertEquals(3, notes.size());
        assertEquals("章节", notes.getFirst().chapter().title());
        assertEquals("其他笔记", notes.get(1).chapter().title());
        assertEquals("其他笔记", notes.get(2).chapter().title());
    }
    @Test void chapterMetadataDoesNotChangeDeliveryIdentityOrContent() {
        var normalizer = new Normalizer(mapper);
        var highlights = List.of(new Highlight("h", "原文", "c", "1-2"));
        var before = normalizer.normalize(book, new SourceNotes(highlights, List.of())).notes().getFirst();
        var after = normalizer.normalize(book, new SourceNotes(highlights, List.of(), List.of(new Chapter("c", "标题", 1)))).notes().getFirst();
        assertEquals(before.id(), after.id());
        assertEquals(before.sourceKeys(), after.sourceKeys());
        assertEquals(before.content(), after.content());
    }
    @Test void chapterParsingToleratesMissingMetadata() throws Exception {
        assertTrue(RemoteApis.parseChapters(mapper.readTree("{}")).isEmpty());
        var chapters = RemoteApis.parseChapters(mapper.readTree("""
            {"chapters":[{"chapterUid":2,"chapterIdx":1,"title":"第一章"},
            {"chapterUid":3,"title":"附录"},{"title":"没有ID"},{"chapterUid":4}]}
            """));
        assertEquals(2, chapters.size());
        assertEquals(new Chapter("2", "第一章", 1), chapters.getFirst());
        assertEquals(Integer.MAX_VALUE - 1, chapters.get(1).order());
    }
    @Test void emptyReviewDoesNotBlockOtherNotesOrBecomeDeliveredSource() throws Exception {
        var empty = RemoteApis.parseReview(mapper.readTree("""
            {"reviewId":"empty","content":"","chapterUid":1,"range":"1-3"}
            """));
        var source = new SourceNotes(List.of(new Highlight("h","原文","1","1-3")),
            List.of(empty, new Review("valid","有效想法","","1","1-3"),
                new Review("blank","  \n  ","原文","2","4-5")));
        var result = new Normalizer(mapper).normalize(new Book("b","测试书","作者",1,3), source);
        assertEquals(1, result.notes().size());
        assertEquals("原文", result.notes().getFirst().quote());
        assertEquals("有效想法", result.notes().getFirst().thought());
        assertEquals(2, result.notes().getFirst().sourceKeys().size());
        assertTrue(result.warnings().isEmpty());
    }
    @Test void emptyReviewCanBeImportedWhenContentAppearsLater() throws Exception {
        var normalizer = new Normalizer(mapper);
        var empty = new SourceNotes(List.of(), List.of(new Review("r","","","","")));
        assertTrue(normalizer.normalize(book, empty).notes().isEmpty());
        var filled = new SourceNotes(List.of(), List.of(new Review("r","补回的想法","","","")));
        assertEquals(1, normalizer.normalize(book, filled).notes().size());
    }
    @Test void sourceKeyMapsBackToBook() {
        var normalizer=new Normalizer(mapper);
        var note=normalizer.normalize(book,new SourceNotes(List.of(new Highlight("h","原文","1","1-3")),List.of())).notes().getFirst();
        assertEquals("b",normalizer.bookOf(note.sourceKeys().getFirst()));
        assertNull(normalizer.bookOf("legacy-key"));
    }
    @Test void independentReviewIsSeparate() {
        var result=new Normalizer(mapper).normalize(book,new SourceNotes(List.of(new Highlight("h","原文","1","1-3")),List.of(new Review("r","想法","","2","1-3"))));
        assertEquals(2,result.notes().size()); assertFalse(result.notes().getFirst().content().contains("────────"));
    }
    @Test void ambiguousRangeDoesNotMerge() {
        var result=new Normalizer(mapper).normalize(book,new SourceNotes(List.of(new Highlight("h","原文","1","1-3"),new Highlight("h2","原文2","1","1-3")),List.of(new Review("r","想法","","1","1-3"))));
        assertEquals(3,result.notes().size()); assertTrue(result.warnings().isEmpty());
    }
    @Test void tagEscapesWhitespaceAndSeparators() { assertEquals("#微信读书/《A＿B／＃》",Normalizer.tag("A B/#")); }
    @Test void emptyContentStops() { assertThrows(ApiException.class,() -> new Normalizer(mapper).normalize(book,new SourceNotes(List.of(new Highlight("h","","1","")),List.of()))); }
    @Test void validatesFlomoOrigin() {
        assertTrue(RemoteApis.validFlomoUrl("https://flomoapp.com/iwh/1/test-key/"));
        for(String url:List.of("http://flomoapp.com/iwh/1/key/","https://evil.example/iwh/1/key/","https://flomoapp.com/iwh/1/key/?x=y","https://user@flomoapp.com/iwh/1/key/")) assertFalse(RemoteApis.validFlomoUrl(url));
    }
    @Test void realFlomoReceiptUsesTopLevelMemo() throws Exception {
        assertTrue(RemoteApis.saved(mapper.readTree("{\"code\":0,\"message\":\"已记录\",\"memo\":{\"slug\":\"record\"}}")));
        assertFalse(RemoteApis.saved(mapper.readTree("{\"code\":0,\"data\":{\"slug\":\"record\"}}")));
        assertFalse(RemoteApis.saved(mapper.readTree("{\"code\":1,\"memo\":{\"slug\":\"record\"}}")));
        assertFalse(RemoteApis.saved(mapper.readTree("{\"code\":0,\"memo\":{\"slug\":\"\"}}")));
    }
    private NotebookService setup(RemoteApis remote,Ledger ledger,UUID user) {
        when(remote.books(user)).thenReturn(List.of(book));
        when(remote.status(any())).thenReturn(Map.of("sendingEnabled",true));
        when(remote.notes(user,"b")).thenReturn(new SourceNotes(List.of(new Highlight("h1","一","",""),new Highlight("h2","二","",""),new Highlight("h3","三","","")),List.of()));
        when(ledger.status(eq(user),any())).thenReturn("ready");
        var service=new NotebookService(remote,new Normalizer(mapper),ledger,mock(Credentials.class)); service.books(user); return service;
    }
    @Test void localDeleteRequiresConfirmationAndOwnershipAndSurvivesReload() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var dismissed=new HashSet<String>();
        when(ledger.dismissedSources(user)).thenReturn(dismissed);
        doAnswer(invocation -> { dismissed.addAll(((Note)invocation.getArgument(1)).sourceKeys()); return null; }).when(ledger).dismiss(eq(user),any());
        var p=service.preview(user,"b"); String id=p.notes().getFirst().id();
        assertThrows(ApiException.class,() -> service.delete(user,new DeleteRequest(p.previewId(),id,false)));
        assertThrows(ApiException.class,() -> service.delete(UUID.randomUUID(),new DeleteRequest(p.previewId(),id,true)));
        assertThrows(ApiException.class,() -> service.delete(user,new DeleteRequest(p.previewId(),"unknown",true)));
        assertEquals(2,service.delete(user,new DeleteRequest(p.previewId(),id,true)).size());
        assertEquals(2,service.preview(user,"b").notes().size());
        assertThrows(ApiException.class,() -> service.send(user,new SendRequest(p.previewId(),List.of(id),true)));
        // A sent-and-deleted source must be subtracted only once from shelf counts.
        when(ledger.sourceStatuses(user)).thenReturn(Map.of(id,"sent"));
        assertEquals(new SyncStats(0,0,1),service.syncStats(user).get("b"));
        verify(remote,never()).send(any(),anyString());
    }
    @Test void thoughtOverridesPersistAcrossPreviewsAndAreUsedWhenSending() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var thoughts=new HashMap<String,String>();
        when(ledger.thoughts(user)).thenReturn(thoughts);
        doAnswer(invocation -> { thoughts.put(invocation.getArgument(1),invocation.getArgument(2)); return null; }).when(ledger).saveThought(eq(user),anyString(),anyString());
        var p=service.preview(user,"b"); String id=p.notes().getFirst().id();
        assertThrows(ApiException.class,() -> service.saveThought(UUID.randomUUID(),new ThoughtRequest(p.previewId(),id,"越权")));
        assertThrows(ApiException.class,() -> service.saveThought(user,new ThoughtRequest(p.previewId(),"unknown","想法")));
        assertThrows(ApiException.class,() -> service.saveThought(user,new ThoughtRequest(p.previewId(),id,null)));
        assertThrows(ApiException.class,() -> service.saveThought(user,new ThoughtRequest(p.previewId(),id,"字".repeat(10001))));
        var updated=service.saveThought(user,new ThoughtRequest(p.previewId(),id," 新想法 ")).getFirst();
        assertEquals("新想法",updated.thought()); assertEquals("一",updated.quote()); assertEquals(1,updated.sources());
        assertEquals("新想法",service.preview(user,"b").notes().getFirst().thought());
        // Even an older preview sends the latest saved thought, without changing identity.
        service.send(user,new SendRequest(p.previewId(),List.of(id),true));
        verify(remote).send(user,"#微信读书/《测试书》\n\n**原文：**\n\n一\n\n────────\n\n**想法：**\n\n新想法");
        when(ledger.status(eq(user),any())).thenReturn("sent");
        assertEquals("sent",service.saveThought(user,new ThoughtRequest(p.previewId(),id,"再次修改")).getFirst().status());
        assertThrows(ApiException.class,() -> service.send(user,new SendRequest(p.previewId(),List.of(id),true)));
        verify(remote,times(1)).send(eq(user),anyString());
        assertEquals("",service.saveThought(user,new ThoughtRequest(p.previewId(),id,"")).getFirst().thought());
        when(ledger.dismissedSources(user)).thenReturn(Set.of(id));
        assertThrows(ApiException.class,() -> service.saveThought(user,new ThoughtRequest(p.previewId(),id,"已删除")));
    }
    @Test void successfulBatchDoesNotStopAfterFirst() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var p=service.preview(user,"b"); service.send(user,new SendRequest(p.previewId(),p.notes().stream().map(PreviewNote::id).toList(),true));
        verify(remote,times(3)).send(eq(user),anyString()); verify(ledger,times(3)).finish(eq(user),anyString(),eq("sent"));
    }
    @Test void timeoutStopsRemainingWithoutRetry() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        doThrow(new ApiException(502,"超时")).when(remote).send(eq(user),anyString());
        var p=service.preview(user,"b"); assertEquals("超时",service.send(user,new SendRequest(p.previewId(),p.notes().stream().map(PreviewNote::id).toList(),true)).deliveryError());
        verify(remote,times(1)).send(eq(user),anyString()); verify(ledger).finish(eq(user),anyString(),eq("uncertain"));
    }
    @Test void ownerIsolationAndConfirmation() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var p=service.preview(user,"b"); var ids=p.notes().stream().map(PreviewNote::id).toList();
        assertThrows(ApiException.class,() -> service.send(UUID.randomUUID(),new SendRequest(p.previewId(),ids,true)));
        assertThrows(ApiException.class,() -> service.send(user,new SendRequest(p.previewId(),ids,false)));
        verify(remote,never()).send(any(),anyString());
    }
    @Test void sentNotesCannotBeSentAgain() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var p=service.preview(user,"b"); when(ledger.status(eq(user),any())).thenReturn("sent");
        assertThrows(ApiException.class,() -> service.send(user,new SendRequest(p.previewId(),List.of(p.notes().getFirst().id()),true)));
        verify(remote,never()).send(any(),anyString());
    }
    @Test void resolvingUncertainRequiresChoiceAndOwnPreview() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var p=service.preview(user,"b"); String id=p.notes().getFirst().id();
        assertThrows(ApiException.class,() -> service.resolve(user,new ResolveRequest(p.previewId(),id,null)));
        assertThrows(ApiException.class,() -> service.resolve(UUID.randomUUID(),new ResolveRequest(p.previewId(),id,true)));
        service.resolve(user,new ResolveRequest(p.previewId(),id,false));
        verify(ledger).resolveUncertain(eq(user),argThat(n -> n.id().equals(id)),eq(false));
        verify(remote,never()).send(any(),anyString());
    }
    @Test void changingCredentialsInvalidatesPreviewWithoutResettingLedger() {
        var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class); var user=UUID.randomUUID(); var service=setup(remote,ledger,user);
        var p=service.preview(user,"b");
        service.saveCredentials(user,new Credentials.Change("wrk-test",null));
        assertThrows(ApiException.class,() -> service.send(user,new SendRequest(p.previewId(),List.of(p.notes().getFirst().id()),true)));
        assertThrows(ApiException.class,() -> service.preview(user,"b"));
        verify(remote,never()).send(any(),anyString());
    }
    @Test void parsesCoverAndProgressWithUnknownStatePreserved() throws Exception {
        var result=RemoteApis.parseBook(mapper.readTree("{\"bookId\":\"1\",\"book\":{\"title\":\"书\",\"cover\":\"https://cdn.weread.qq.com/cover.jpg\"},\"readingProgress\":30,\"markedStatus\":2}"));
        assertEquals("https://cdn.weread.qq.com/cover.jpg",result.cover());
        assertEquals(30d,result.readingProgress()); assertEquals(2,result.markedStatus());
    }
    @Test void coverUrlsCannotTargetPrivateOrUntrustedHosts() {
        for(String url:List.of("http://cdn.weread.qq.com/a","https://127.0.0.1/a","https://cdn.weread.qq.com.evil.example/a","https://user@cdn.weread.qq.com/a","data:image/svg+xml,abc")) assertEquals("",RemoteApis.coverUrl(url));
        assertEquals("https://wfqqreader-1252317822.image.myqcloud.com/a",RemoteApis.coverUrl("https://wfqqreader-1252317822.image.myqcloud.com/a"));
    }
}
