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
