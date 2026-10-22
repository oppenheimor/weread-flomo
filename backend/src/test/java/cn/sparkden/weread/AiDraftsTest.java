package cn.sparkden.weread;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.sparkden.weread.Models.*;
import static cn.sparkden.weread.AiModels.*;

class AiDraftsTest {
    Snapshot snapshot() {
        var chapter=new Chapter("c","前言",1);
        return new Snapshot(new Book("b","书","作者",3,0),chapter,List.of(
            new Note("a","b","highlight",List.of("a"),"内容一","原文一","想法一",chapter,1L),
            new Note("b","b","highlight",List.of("b"),"内容二","原文二","",chapter,2L),
            new Note("c","b","highlight",List.of("c"),"内容三","原文三","想法三",chapter,3L)));
    }
    @Test void showsPartialTitleThenBodyWithoutLeakingJsonSyntax() {
        String prefix="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"先安排";
        var cards=AiDrafts.read(prefix,snapshot());
        assertEquals(1,cards.size()); assertEquals("先安排",cards.getFirst().title()); assertEquals("",cards.getFirst().body());
        cards=AiDrafts.read(prefix+"时间\",\"body\":\"通过 **输出**\\n检验",snapshot());
        assertEquals("先安排时间",cards.getFirst().title()); assertEquals("通过 **输出**\n检验",cards.getFirst().body());
    }
    @Test void handlesEveryPrefixEscapesBracesAndUnicodeWithoutInventingCharacters() {
        String output="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"标题\",\"body\":\"引号\\\"与括号{}[]、\\u4e2d文\\n末尾\"}]}";
        String expected="引号\"与括号{}[]、中文\n末尾";
        for(int i=0;i<=output.length();i++) {
            var cards=AiDrafts.read(output.substring(0,i),snapshot());
            if(!cards.isEmpty()) assertTrue(expected.startsWith(cards.getFirst().body()),"prefix "+i);
        }
        assertEquals(expected,AiDrafts.read(output,snapshot()).getFirst().body());
    }
    @Test void singletonsUseTheSnapshotAndWaitForCompleteSourceIds() {
        assertTrue(AiDrafts.read("{\"groups\":[{\"sourceIds\":[\"0\"",snapshot()).isEmpty());
        var cards=AiDrafts.read("{\"groups\":[{\"sourceIds\":[\"2\"],\"title\":\"invented\",\"body\":\"invented\"}]}",snapshot());
        assertEquals("excerpt",cards.getFirst().kind()); assertEquals("原文三",cards.getFirst().quote()); assertEquals("想法三",cards.getFirst().thought()); assertEquals("",cards.getFirst().body());
    }
    @Test void ignoresUnknownReferencesAndTextMasqueradingAsFields() {
        assertTrue(AiDrafts.read("{\"groups\":[{\"sourceIds\":[\"9\"],\"title\":\"bad\"}]}",snapshot()).isEmpty());
        assertTrue(AiDrafts.read("{\"summary\":\"伪造 \\\"groups\\\": []\"}",snapshot()).isEmpty());
        assertTrue(AiDrafts.read("{\"groups\":[{\"sourceIds\":[\"0\",\"0\"],\"title\":\"bad\"}]}",snapshot()).isEmpty());
    }
    @Test void aDraftNeverMakesAnIncompleteAnswerValid() {
        String partial="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"标题\",\"body\":\"正文";
        assertEquals(1,AiDrafts.read(partial,snapshot()).size());
        assertThrows(ApiException.class,() -> new AiOrganizer(new com.fasterxml.jackson.databind.ObjectMapper()).validate(partial,snapshot()));
    }
}
