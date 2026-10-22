package cn.sparkden.weread;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.sparkden.weread.AiModels.*;

class AiTransportTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void protocolsSerializeAndParseWithoutExposingKeysInUrls() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        var paths=new ArrayList<String>();
        server.createContext("/", exchange -> {
            String path=exchange.getRequestURI().toString(); paths.add(path);
            var body=mapper.readTree(exchange.getRequestBody()); String reply;
            if(path.equals("/v1/messages")) {
                assertEquals("test-key",exchange.getRequestHeaders().getFirst("x-api-key")); assertEquals("2023-06-01",exchange.getRequestHeaders().getFirst("anthropic-version"));
                assertEquals("system",body.path("system").asText());
                reply="{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"anthropic-ok\"}]}";
            } else if(path.equals("/models/gemini-test:generateContent")) {
                assertEquals("test-key",exchange.getRequestHeaders().getFirst("x-goog-api-key")); assertEquals("system",body.path("systemInstruction").path("parts").get(0).path("text").asText());
                reply="{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"private-reasoning\",\"thought\":true},{\"text\":\"google-ok\"}]}}]}";
            } else {
                assertEquals("/chat/completions",path); assertEquals("Bearer test-key",exchange.getRequestHeaders().getFirst("Authorization")); assertEquals("system",body.path("messages").get(0).path("content").asText());
                reply="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"openai-ok\"}}]}";
            }
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); var transport=new AiTransport(mapper,"localhost");
        try {
            String base="http://localhost:"+server.getAddress().getPort();
            assertEquals("openai-ok",transport.generate(new Connection("openai",base,"test-key","model"),"system","prompt",100));
            assertEquals("anthropic-ok",transport.generate(new Connection("anthropic",base,"test-key","model"),"system","prompt",100));
            assertEquals("google-ok",transport.generate(new Connection("google",base,"test-key","gemini-test"),"system","prompt",100));
            assertEquals(3,paths.size()); assertTrue(paths.stream().noneMatch(p -> p.contains("test-key")));
        } finally { transport.close(); server.stop(0); }
    }
    @Test void deepSeekDefaultsToFastAndOnlyExplicitlyEnablesThinking() throws Exception {
        var fast=new Connection("openai","https://api.deepseek.com","test","deepseek-v4-pro");
        assertEquals(Map.of("type","disabled"),AiTransport.streamingBody(fast,"s","p",100).get("thinking"));
        assertEquals(Map.of("type","enabled"),AiTransport.streamingBody(fast.withThinking(true),"s","p",100).get("thinking"));
        assertTrue(AiTransport.supportsThinking("openai","deepseek-flash"));
        assertFalse(AiTransport.supportsThinking("anthropic","deepseek-v4-pro"));
        assertFalse(AiTransport.streamingBody(new Connection("openai","https://example.com","test","other-model"),"s","p",100).containsKey("thinking"));
        assertFalse(mapper.readValue("{\"previewId\":\"p\",\"chapterId\":\"c\",\"replaceConfirmed\":false}",AiModels.Start.class).deepThinking());
    }
    @Test void streamingRequestUsesSseAndForwardsOnlyPublicText() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        var requestedStream=new AtomicBoolean();
        server.createContext("/chat/completions",exchange -> {
            requestedStream.set(mapper.readTree(exchange.getRequestBody()).path("stream").asBoolean());
            exchange.getResponseHeaders().add("Content-Type","text/event-stream"); exchange.sendResponseHeaders(200,0);
            String data="data: {\"choices\":[{\"delta\":{\"content\":\"**重点**\"}}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
            exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8)); exchange.close();
        });
        server.start(); var transport=new AiTransport(mapper,"localhost");
        try {
            var updates=new ArrayList<String>();
            assertEquals("**重点**",transport.generateStreaming(new Connection("openai","http://localhost:"+server.getAddress().getPort(),"test-key","model"),"system","prompt",100,updates::add));
            assertTrue(requestedStream.get()); assertEquals(List.of("**重点**"),updates);
        } finally { transport.close(); server.stop(0); }
    }
    @Test void noAutomaticRetryOrRedirectAndPrivateHostRequiresAdminOptIn() throws Exception {
        var count=new AtomicInteger(); var code=new AtomicInteger(429);
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/", exchange -> { count.incrementAndGet(); exchange.getResponseHeaders().add("Location","http://localhost:1/secret"); exchange.sendResponseHeaders(code.get(),-1); exchange.close(); });
        server.start(); var allowed=new AiTransport(mapper,"localhost"); var denied=new AiTransport(mapper,"");
        try {
            var c=new Connection("openai","http://localhost:"+server.getAddress().getPort(),"secret","model");
            assertThrows(ApiException.class,() -> allowed.generate(c,"s","p",100)); assertEquals(1,count.get());
            code.set(302); assertThrows(ApiException.class,() -> allowed.generate(c,"s","p",100)); assertEquals(2,count.get());
            assertThrows(ApiException.class,() -> denied.generate(c,"s","p",100)); assertEquals(2,count.get());
        } finally { allowed.close(); denied.close(); server.stop(0); }
    }
}
