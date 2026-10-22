package cn.sparkden.weread;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.sparkden.weread.Models.*;

class AiSnapshotTest {
    @Test void snapshotOnlyContainsCurrentChapterReadyNotesAndAppliesUserThoughts() {
        UUID user=UUID.randomUUID(); var remote=mock(RemoteApis.class); var ledger=mock(Ledger.class);
        var book=new Book("book","书","作者",3,0); var chapter=new Chapter("one","第一章",1); var other=new Chapter("two","第二章",2);
        when(remote.books(user)).thenReturn(List.of(book));
        when(remote.notes(user,"book")).thenReturn(new SourceNotes(List.of(new Highlight("h1","待整理","one","1-2"),new Highlight("h2","已同步","one","3-4"),new Highlight("h3","其他章节","two","1-2")),List.of(),List.of(chapter,other)));
        when(ledger.dismissedSources(user)).thenReturn(Set.of()); when(ledger.thoughts(user)).thenReturn(Map.of());
        when(ledger.status(eq(user),any())).thenAnswer(call -> ((Note)call.getArgument(1)).quote().equals("已同步")?"sent":"ready");
        var notebook=new NotebookService(remote,new Normalizer(new ObjectMapper()),ledger,mock(Credentials.class));
        notebook.books(user); var preview=notebook.preview(user,"book");
        String id=preview.notes().stream().filter(n -> n.quote().equals("待整理")).findFirst().orElseThrow().id();
        when(ledger.thoughts(user)).thenReturn(Map.of(id,"用户修改后的想法"));
        var snapshot=notebook.aiSnapshot(user,preview.previewId(),"one");
        assertEquals(1,snapshot.notes().size()); assertEquals("待整理",snapshot.notes().getFirst().quote()); assertEquals("用户修改后的想法",snapshot.notes().getFirst().thought());
        assertThrows(ApiException.class,() -> notebook.aiSnapshot(UUID.randomUUID(),preview.previewId(),"one"));
        assertThrows(ApiException.class,() -> notebook.aiSnapshot(user,preview.previewId(),"missing"));
        when(ledger.status(eq(user),any())).thenReturn("sent");
        assertThrows(ApiException.class,() -> notebook.aiSnapshot(user,preview.previewId(),"one"));
    }
}
