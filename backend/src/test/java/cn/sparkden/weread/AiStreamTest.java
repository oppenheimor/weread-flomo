package cn.sparkden.weread;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiStreamTest {
    final ObjectMapper mapper=new ObjectMapper();
    String read(String protocol,String s,List<String> updates) throws Exception {
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
        var input=new ByteArrayInputStream(bytes) { @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(1,len)); } };
        return AiStream.read(input,protocol,mapper,updates::add);
    }
    @Test void openaiStreamsPublicContentNotReasoningAndPreservesBold() throws Exception {
        var updates=new ArrayList<String>();
        String answer=read("openai","""
            data: {"choices":[{"delta":{"reasoning_content":"private reasoning"}}]}

            data: {"choices":[{"delta":{"content":"**关键"}}]}

            data: {"choices":[{"delta":{"content":"观点**"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """,updates);
        assertEquals("**关键观点**",answer); assertEquals(List.of("**关键","**关键观点**"),updates);
    }
    @Test void sseFramingAndThinkingDoNotConsumeThePublicAnswerLimit() throws Exception {
        // A normal long-thinking answer can exceed 2 MB on the wire from per-token SSE envelopes.
        String event="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"not forwarded\"}}],\"metadata\":\""+"x".repeat(300)+"\"}\n\n";
        String stream=event.repeat(6000)+"data: {\"choices\":[{\"delta\":{\"content\":\"正文\"},\"finish_reason\":\"stop\"}]}\n\n";
        assertTrue(stream.getBytes(StandardCharsets.UTF_8).length>2_000_000);
        var updates=new ArrayList<String>();
        assertEquals("正文",AiStream.read(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),"openai",mapper,updates::add));
        assertEquals(List.of("正文"),updates);
    }
    @Test void publicTextStillHasAnIndependentSizeLimit() {
        String event="data: {\"choices\":[{\"delta\":{\"content\":\""+"x".repeat(110_000)+"\"}}]}\n\n";
        var error=assertThrows(ApiException.class,() -> AiStream.read(new ByteArrayInputStream(event.repeat(20).getBytes(StandardCharsets.UTF_8)),"openai",mapper,ignored -> {}));
        assertTrue(error.getMessage().contains("正文超过 2 MB"));
    }
    @Test void reportsActualThinkingOnceWithoutPublishingItsText() throws Exception {
        var events=new ArrayList<String>();
        String stream="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private\"}}]}\n\n".repeat(2)
            +"data: {\"choices\":[{\"delta\":{\"content\":\"正文\"},\"finish_reason\":\"stop\"}]}\n\n";
        AiStream.read(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),"openai",mapper,new AiStream.Listener() {
            public void phase(String phase) { events.add("phase:"+phase); }
            public void accept(String text) { events.add(text); }
        });
        assertEquals(List.of("phase:thinking","正文"),events);
    }
    @Test void anthropicFiltersThinkingEvents() throws Exception {
        var updates=new ArrayList<String>();
        assertEquals("正文",read("anthropic","""
            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"private"}}

            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"正文"}}

            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}

            data: {"type":"message_stop"}

            """,updates));
        assertEquals(List.of("正文"),updates);
    }
    @Test void googleFiltersThoughtParts() throws Exception {
        var updates=new ArrayList<String>();
        assertEquals("正文",read("google","""
            data: {"candidates":[{"content":{"parts":[{"thought":true,"text":"private"},{"text":"正文"}]},"finishReason":"STOP"}]}

            """,updates));
        assertEquals(List.of("正文"),updates);
    }
    @Test void disconnectedAndTruncatedStreamsNeverCountAsSuccess() {
        for(String s:List.of("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n", "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\n", "data: [DONE]\n\n")) assertThrows(ApiException.class,() -> read("openai",s,new ArrayList<>()));
    }
    @Test void summaryOnlyReadsCompletedTopLevelPublicDescription() {
        assertEquals("",AiService.summary("{\"summary\":\"半句话"));
        assertEquals("方法与框架",AiService.summary("{\"summary\":\"方法与框架\",\"groups\":["));
        assertEquals("",AiService.summary("{\"groups\":[{\"summary\":\"不应该展示\"}]}"));
        assertEquals("含\"引号\"",AiService.summary("{\"summary\":\"含\\\"引号\\\"\"}"));
    }
}
