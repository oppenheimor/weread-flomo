package cn.sparkden.weread;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.sparkden.weread.Models.*;
import static cn.sparkden.weread.AiModels.*;

class AiOrganizerTest {
    final AiOrganizer organizer=new AiOrganizer(new ObjectMapper());
    Snapshot snapshot() {
        var book=new Book("b","书","作者",2,0); var chapter=new Chapter("c","前言",1);
        return new Snapshot(book,chapter,List.of(new Note("a","b","highlight",List.of("a"),"原样内容一","原文一","想法一",chapter,1L),new Note("b","b","review",List.of("b"),"原样内容二","原文二","想法二",chapter,2L)));
    }
    @Test void boldMarkdownSurvivesFlomoFormattingWithoutChangingSources() {
        var card=new Card("id","标题","使用 **关键方法**，形成 **完整观点**。",snapshot(),"ready",false,1);
        String content=AiOrganizer.content(card);
        assertTrue(content.contains("使用 **关键方法**，形成 **完整观点**。"));
        assertTrue(content.contains("原文：原文一\n想法：想法一"));
        assertFalse(content.contains("<strong>"));
    }
    @Test void acceptsExactCoverageAndKeepsImmutableSourcesInReadingOrder() {
        var groups=organizer.validate("{\"groups\":[{\"sourceIds\":[\"1\",\"0\"],\"title\":\"标题\",\"body\":\"观点\"}]}",snapshot());
        assertEquals(List.of("a","b"),groups.getFirst().snapshot().notes().stream().map(Note::id).toList());
        var card=new Card("id",groups.getFirst().title(),groups.getFirst().body(),groups.getFirst().snapshot(),"ready",false,1);
        String text=AiOrganizer.content(card);
        assertTrue(text.startsWith("#微信读书/《书》\n\n前言｜标题\n\n观点"));
        assertTrue(text.contains("来源 1\n原文：原文一\n想法：想法一")); assertTrue(text.contains("来源 2\n原文：原文二\n想法：想法二"));
    }
    @Test void rejectsMissingRepeatedInventedAndTrailingOutput() {
        for(String json:List.of(
            "{\"groups\":[{\"sourceIds\":[\"0\"]}]}",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"0\",\"1\"],\"title\":\"t\",\"body\":\"b\"}]}",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"8\"],\"title\":\"t\",\"body\":\"b\"}]}",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"t\",\"body\":\"b\"}]} {}")) assertThrows(ApiException.class,() -> organizer.validate(json,snapshot()));
    }
    @Test void validationExplainsTheSpecificFailureWithoutExposingNoteContent() {
        var cases=Map.of(
            "{\"groups\":[{\"sourceIds\":[\"0\"]}]}", "遗漏了 1 条来源",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"0\",\"1\"],\"title\":\"t\",\"body\":\"b\"}]}", "重复使用",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"8\"],\"title\":\"t\",\"body\":\"b\"}]}", "不存在的来源",
            "{\"groups\":[{\"sourceIds\":[0,1],\"title\":\"t\",\"body\":\"b\"}]}", "来源编号格式",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"t\",\"body\":\"\"}]}", "缺少正文",
            "{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\""+"长".repeat(121)+"\",\"body\":\"b\"}]}", "标题超过 120 字",
            "{\"groups\":[", "JSON",
            "{\"groups\":[]} {}", "额外内容"
        );
        cases.forEach((text,reason) -> {
            var error=assertThrows(ApiException.class,() -> organizer.validate(text,snapshot()));
            assertTrue(error.getMessage().contains(reason),error.getMessage());
            assertTrue(error.getMessage().contains("旧结果未改动"));
            assertFalse(error.getMessage().contains("原文一"));
        });
    }
    @Test void blankTitleInMultiSourceGroupReproducesReportedFailure() {
        for(String title:List.of("", "   ", "\n\t")) {
            var group=new LinkedHashMap<String,Object>();
            group.put("sourceIds",List.of("0","1")); group.put("title",title); group.put("body","完整正文");
            String text=new ObjectMapper().valueToTree(Map.of("groups",List.of(group))).toString();
            var error=assertThrows(ApiException.class,() -> organizer.validate(text,snapshot()));
            assertEquals("AI 整理失败：第 1 组缺少标题。旧结果未改动，请重新生成或更换模型",error.getMessage());
        }
        // Same sources and body succeed when only the title is corrected.
        var groups=organizer.validate("{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"具体标题\",\"body\":\"完整正文\"}]}",snapshot());
        assertEquals(1,groups.size());
        assertEquals("具体标题",groups.getFirst().title());
    }
    @Test void magpieEmptyMultiSourceGroupBecomesIndependentSourcesWithoutLosingOtherGroups() {
        var base=snapshot();
        var notes=new ArrayList<Note>();
        for(int i=0;i<14;i++) notes.add(new Note("n"+i,"b","highlight",List.of("s"+i),"原样内容"+i,"原文"+i,"想法"+i,base.chapter(),(long)i));
        var snap=new Snapshot(base.book(),base.chapter(),notes);
        // Same grouping and empty fields as the reported Magpie response; private prose replaced.
        String text="""
            {"groups":[
              {"sourceIds":["0","1"],"title":"","body":""},
              {"sourceIds":["2","3","4","5","6","7","8","9"],"title":"标题二","body":"正文二"},
              {"sourceIds":["10","11"],"title":"标题三","body":"正文三"},
              {"sourceIds":["12","13"],"title":"标题四","body":"正文四"}
            ]}
            """;
        var groups=organizer.validate(text,snap);
        assertEquals(List.of(1,1,8,2,2),groups.stream().map(g -> g.snapshot().notes().size()).toList());
        assertEquals(notes,groups.stream().flatMap(g -> g.snapshot().notes().stream()).toList());
        for(int i=0;i<2;i++) {
            var group=groups.get(i);
            assertEquals("",group.title()); assertEquals("",group.body());
            assertEquals(notes.get(i).content(),AiOrganizer.content(new Card("id"+i,"","",group.snapshot(),"ready",false,1)));
        }
        assertEquals(List.of("标题二","标题三","标题四"),groups.subList(2,5).stream().map(AiOrganizer.Group::title).toList());
        assertEquals(List.of("正文二","正文三","正文四"),groups.subList(2,5).stream().map(AiOrganizer.Group::body).toList());
    }
    @Test void emptyGroupRecoveryStillRequiresTextFieldsAndExactSourceCoverage() {
        for(String fields:List.of("",",\"title\":null,\"body\":\"\"",",\"title\":\"\",\"body\":null",",\"title\":\"\",\"body\":{}")) {
            assertThrows(ApiException.class,() -> organizer.validate("{\"groups\":[{\"sourceIds\":[\"0\",\"1\"]"+fields+"}]}",snapshot()));
        }
        for(String ids:List.of("\"0\",\"0\"","\"0\",\"8\"","0,1")) {
            assertThrows(ApiException.class,() -> organizer.validate("{\"groups\":[{\"sourceIds\":["+ids+"],\"title\":\"\",\"body\":\"\"}]}",snapshot()));
        }
        assertThrows(ApiException.class,() -> organizer.validate("{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"\",\"body\":\"\"},{\"sourceIds\":[\"1\"]}]}",snapshot()));
        var base=snapshot();
        var three=new Snapshot(base.book(),base.chapter(),List.of(base.notes().get(0),base.notes().get(1),new Note("c","b","highlight",List.of("c"),"第三条","原文三","",base.chapter(),3L)));
        var missing=assertThrows(ApiException.class,() -> organizer.validate("{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"\",\"body\":\"\"}]}",three));
        assertTrue(missing.getMessage().contains("遗漏了 1 条来源"));
    }
    @Test void singletonsCannotBeRewrittenByModel() {
        var groups=organizer.validate("{\"groups\":[{\"sourceIds\":[\"1\"],\"body\":\"invented\"},{\"sourceIds\":[\"0\"],\"title\":\"invented\"}]}",snapshot());
        assertEquals("",groups.getFirst().body()); assertEquals("",groups.getFirst().title());
        assertEquals("原样内容一",AiOrganizer.content(new Card("a","","",groups.getFirst().snapshot(),"ready",false,1)));
    }
    @Test void refusesOversizedInputsBeforeCallingAi() {
        var snap=snapshot(); var source=snap.notes().getFirst();
        var huge=new Note("x","b","highlight",List.of("x"),"","x".repeat(60001),"",source.chapter(),1L);
        assertThrows(ApiException.class,() -> organizer.prompt(new Snapshot(snap.book(),snap.chapter(),List.of(huge))));
    }
    @Test void privateAndLiteralEndpointsAreRejected() throws Exception {
        for(String url:List.of("https://user:secret@api.example.com","https://api.example.com?key=secret","file:///tmp/a","http://127.0.0.1:8080","https://[::1]/v1")) assertThrows(ApiException.class,() -> AiTransport.validUrl(url));
        for(String address:List.of("127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fc00::1","64:ff9b::a00:1")) assertFalse(AiTransport.publicAddress(InetAddress.getByName(address)));
        assertTrue(AiTransport.publicAddress(InetAddress.getByName("8.8.8.8")));
    }
}
