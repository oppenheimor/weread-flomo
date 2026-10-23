package cn.sparkden.weread;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.sparkden.weread.Models.*;
import static cn.sparkden.weread.AiModels.*;

class AiIncrementalTest {
    final Book book=new Book("b","书","作者",2,0);
    final Chapter chapter=new Chapter("c","第一章",1);
    Note note(String id) { return new Note(id,"b","highlight",List.of(id),"原文","原文","",chapter,1L); }
    Snapshot snapshot(String... ids) { return new Snapshot(book,chapter,Arrays.stream(ids).map(this::note).toList()); }
    Card card(String status) { return new Card("old","手动改过的标题","内容",snapshot("old"),status,true,2); }
    @Test void afterSyncOnlyNewAnnotationsAreOrganized() {
        var result=AiService.incrementalSnapshot(snapshot("new"),List.of(card("sent")),false);
        assertEquals(List.of("new"),result.notes().stream().map(Note::id).toList());
        assertFalse(AiService.replacedBy(card("sent"),result));
    }
    @Test void keepsPendingEditedResultsWhenAppending() {
        var result=AiService.incrementalSnapshot(snapshot("old","new"),List.of(card("ready")),false);
        assertEquals(List.of("new"),result.notes().stream().map(Note::id).toList());
        assertFalse(AiService.replacedBy(card("ready"),result));
    }
    @Test void noNewSourcesMeansNoGeneration() {
        assertTrue(AiService.incrementalSnapshot(snapshot("old"),List.of(card("ready")),false).notes().isEmpty());
    }
    @Test void confirmedRegenerationReplacesPendingButNeverSentResults() {
        var result=AiService.incrementalSnapshot(snapshot("old","new"),List.of(card("ready")),true);
        assertEquals(2,result.notes().size());
        assertTrue(AiService.replacedBy(card("ready"),result));
        assertFalse(AiService.replacedBy(card("sent"),result));
        assertFalse(AiService.replacedBy(card("uncertain"),result));
    }
}
