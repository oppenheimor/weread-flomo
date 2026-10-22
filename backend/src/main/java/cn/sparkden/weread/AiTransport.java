package cn.sparkden.weread;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import static cn.sparkden.weread.AiModels.*;

/** Resolve and validate on the actual connection, not only before the request (DNS rebinding). */
@Service
public class AiTransport {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AiTransport.class);
    private final ObjectMapper mapper;
    private final CloseableHttpClient client;
    private final Set<String> trustedHosts;
    public AiTransport(ObjectMapper mapper,@Value("${app.ai-trusted-hosts:}") String trusted) {
        this.mapper=mapper;
        trustedHosts=new HashSet<>();
        for(String host:trusted.toLowerCase(Locale.ROOT).split(",")) if(!host.strip().isEmpty()) trustedHosts.add(host.strip());
        var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(new DnsResolver() {
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                var addresses=InetAddress.getAllByName(host);
                if(!trustedHosts.contains(host.toLowerCase(Locale.ROOT))) for(var address:addresses) if(!publicAddress(address)) throw new UnknownHostException("Private AI endpoint is not allowed");
                return addresses;
            }
            @Override public String resolveCanonicalHostname(String host) throws UnknownHostException { resolve(host); return host; }
        }).build();
        client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries()
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(10)).setConnectTimeout(Timeout.ofSeconds(15)).setResponseTimeout(Timeout.ofSeconds(120)).build()).build();
    }
    public static URI validUrl(String raw) {
        try {
            if(raw==null || raw.length()>2048) throw new IllegalArgumentException();
            URI uri=URI.create(raw.strip());
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException();
            // Literal private addresses bypass some HTTP resolvers: require DNS hostnames for local servers too.
            if(uri.getHost().contains(":") || uri.getHost().matches("[0-9.]+")) throw new IllegalArgumentException();
            return uri;
        } catch(Exception e) { throw new ApiException(400,"请填写不含凭证、参数的 HTTP(S) API 地址，使用域名而非 IP"); }
    }
    static boolean publicAddress(InetAddress address) {
        if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b=address.getAddress(); int first=b[0]&255;
        if(b.length==16) return (first&0xe0)==0x20; // Only global unicast; excludes ULA, mapped and translation ranges.
        int second=b[1]&255;
        return first!=0 && first<224 && !(first==100 && second>=64 && second<=127) && !(first==169 && second==254) && !(first==192 && (second==0 || second==168)) && !(first==198 && (second==18 || second==19));
    }
    private JsonNode call(Connection c,String path,Object body) { return call(c,path,body,null); }
    private JsonNode call(Connection c,String path,Object body,java.util.function.Consumer<String> onText) {
        long started=System.nanoTime(); String trace=UUID.randomUUID().toString().substring(0,8);
        try {
            validUrl(c.baseUrl());
            URI uri=URI.create(c.baseUrl()+path);
            if(!uri.getScheme().equals("https") && !trustedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) throw new ApiException(400,"公开 AI 接口必须使用 HTTPS，HTTP 仅限管理员允许的本地服务");
            var req=body==null?new HttpGet(uri):new HttpPost(uri);
            req.setHeader("Content-Type","application/json");
            if(!c.key().isBlank()) {
                if(c.protocol().equals("anthropic")) req.setHeader("x-api-key",c.key());
                else if(c.protocol().equals("google")) req.setHeader("x-goog-api-key",c.key());
                else req.setHeader("Authorization","Bearer "+c.key());
            }
            if(c.protocol().equals("anthropic")) req.setHeader("anthropic-version","2023-06-01");
            if(req instanceof HttpPost post) post.setEntity(new StringEntity(mapper.writeValueAsString(body),ContentType.APPLICATION_JSON));
            return client.execute(req,response -> {
                if(response.getCode()<200 || response.getCode()>=300) throw new ApiException(502,"AI 服务返回 HTTP "+response.getCode()+"，请检查地址、模型、权限或额度");
                if(response.getEntity()==null) throw new ApiException(502,"AI 返回空响应");
                try(var input=response.getEntity().getContent()) {
                    String type=response.getEntity().getContentType();
                    boolean streaming=type!=null && type.toLowerCase(Locale.ROOT).contains("text/event-stream");
                    if(onText!=null) {
                        log.info("AI {} headers after {}ms, streaming={}",trace,(System.nanoTime()-started)/1_000_000,streaming);
                        if(onText instanceof AiStream.Listener listener) listener.phase(streaming?"connected":"buffered");
                    }
                    if(onText!=null && streaming) {
                        String text=AiStream.read(input,c.protocol(),mapper,new AiStream.Listener() {
                            boolean first=true;
                            public void phase(String phase) {
                                log.info("AI {} phase={} after {}ms",trace,phase,(System.nanoTime()-started)/1_000_000);
                                if(onText instanceof AiStream.Listener listener) listener.phase(phase);
                            }
                            public void accept(String partial) {
                                if(first) { log.info("AI {} first public text after {}ms",trace,(System.nanoTime()-started)/1_000_000); first=false; }
                                onText.accept(partial);
                            }
                        });
                        log.info("AI {} complete after {}ms, publicChars={}",trace,(System.nanoTime()-started)/1_000_000,text.length());
                        return mapper.createObjectNode().put("_streamText",text);
                    }
                    byte[] data=input.readNBytes(2_000_001);
                    if(data.length>2_000_000) throw new ApiException(502,"AI 响应过大");
                    return mapper.readTree(new String(data,StandardCharsets.UTF_8));
                }
            });
        } catch(ApiException e) { throw e; }
        catch(Exception e) {
            // Exception messages and response bodies may contain private URLs or provider data. Log types only.
            Throwable root=e; while(root.getCause()!=null && root.getCause()!=root) root=root.getCause();
            log.warn("AI {} failed after {}ms, type={}, rootType={}",trace,(System.nanoTime()-started)/1_000_000,e.getClass().getSimpleName(),root.getClass().getSimpleName());
            if(root instanceof java.net.SocketTimeoutException) throw new ApiException(502,"等待 AI 服务响应超时，旧结果未改动，请手动重试");
            if(e instanceof com.fasterxml.jackson.core.JsonProcessingException) throw new ApiException(502,"AI 服务返回了无效的 JSON 数据，旧结果未改动，请检查接口兼容性");
            throw new ApiException(502,"AI 连接失败或响应中断，旧结果未改动；不会自动重试");
        }
    }
    public String generate(Connection c,String system,String prompt,int maxTokens) {
        JsonNode result;
        if(c.protocol().equals("anthropic")) {
            result=call(c,"/v1/messages",Map.of("model",c.model(),"max_tokens",maxTokens,"system",system,"messages",List.of(Map.of("role","user","content",prompt))));
            if("max_tokens".equals(result.path("stop_reason").asText())) throw new ApiException(502,"AI 输出被截断，请换用支持更长输出的模型");
            StringBuilder text=new StringBuilder(); for(var part:result.path("content")) if(part.path("type").asText().equals("text")) text.append(part.path("text").asText()); return text.toString();
        }
        if(c.protocol().equals("google")) {
            String model=c.model().replaceFirst("^models/","");
            result=call(c,"/models/"+model+":generateContent",Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",system))),"contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",prompt)))),"generationConfig",Map.of("maxOutputTokens",maxTokens)));
            var candidate=result.path("candidates").path(0);
            if(!candidate.path("finishReason").asText().equals("STOP")) throw new ApiException(502,"AI 输出未正常完成，请检查模型或内容限制");
            StringBuilder text=new StringBuilder(); for(var part:candidate.path("content").path("parts")) if(!part.path("thought").asBoolean()) text.append(part.path("text").asText("")); return text.toString();
        }
        result=call(c,"/chat/completions",Map.of("model",c.model(),(c.model().matches("(?i)(gpt-5.*|o[134].*)") ? "max_completion_tokens" : "max_tokens"),maxTokens,"messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",prompt))));
        var choice=result.path("choices").path(0);
        if(!"stop".equals(choice.path("finish_reason").asText())) throw new ApiException(502,"AI 输出未正常完成或被截断");
        return choice.path("message").path("content").asText("");
    }
    // Verified DeepSeek Chat Completions contract. Do not inject vendor fields into other models.
    static boolean supportsThinking(String protocol,String model) {
        return "openai".equals(protocol) && Set.of("deepseek-v4-pro","deepseek-flash").contains(model.toLowerCase(Locale.ROOT));
    }
    static Map<String,Object> streamingBody(Connection c,String system,String prompt,int maxTokens) {
        var body=new LinkedHashMap<String,Object>();
        body.put("model",c.model()); body.put("stream",true);
        body.put(c.model().matches("(?i)(gpt-5.*|o[134].*)")?"max_completion_tokens":"max_tokens",maxTokens);
        body.put("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",prompt)));
        if(supportsThinking(c.protocol(),c.model())) body.put("thinking",Map.of("type",c.deepThinking()?"enabled":"disabled"));
        return body;
    }
    public String generateStreaming(Connection c,String system,String prompt,int maxTokens,java.util.function.Consumer<String> onText) {
        JsonNode result;
        switch(c.protocol()) {
            case "anthropic" -> result=call(c,"/v1/messages",Map.of("model",c.model(),"stream",true,"max_tokens",maxTokens,"system",system,"messages",List.of(Map.of("role","user","content",prompt))),onText);
            case "google" -> result=call(c,"/models/"+c.model().replaceFirst("^models/","")+":streamGenerateContent?alt=sse",Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",system))),"contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",prompt)))),"generationConfig",Map.of("maxOutputTokens",maxTokens)),onText);
            default -> result=call(c,"/chat/completions",streamingBody(c,system,prompt,maxTokens),onText);
        }
        if(result.has("_streamText")) return result.get("_streamText").asText();
        // Some compatible endpoints ignore stream=true. Consume that same response, never issue a second paid call.
        String text;
        if(c.protocol().equals("anthropic")) {
            if(!Set.of("end_turn","stop_sequence").contains(result.path("stop_reason").asText())) throw new ApiException(502,"AI 输出未正常完成");
            var out=new StringBuilder(); for(var part:result.path("content")) if(part.path("type").asText().equals("text")) out.append(part.path("text").asText("")); text=out.toString();
        } else if(c.protocol().equals("google")) {
            var candidate=result.path("candidates").path(0);
            if(!candidate.path("finishReason").asText().equals("STOP")) throw new ApiException(502,"AI 输出未正常完成");
            var out=new StringBuilder(); for(var part:candidate.path("content").path("parts")) if(!part.path("thought").asBoolean()) out.append(part.path("text").asText("")); text=out.toString();
        } else {
            var choice=result.path("choices").path(0);
            if(!choice.path("finish_reason").asText().equals("stop")) throw new ApiException(502,"AI 输出未正常完成");
            text=choice.path("message").path("content").asText("");
        }
        if(text.isBlank()) throw new ApiException(502,"AI 返回空内容");
        onText.accept(text); return text;
    }
    public List<String> discover(Connection c) {
        var result=call(c,c.protocol().equals("anthropic")?"/v1/models":"/models",null);
        var ids=new LinkedHashSet<String>();
        for(var item:result.path(c.protocol().equals("google")?"models":"data")) {
            String id=item.path(c.protocol().equals("google")?"name":"id").asText().replaceFirst("^models/","");
            if(AiProviderService.validModel(id)) ids.add(id);
            if(ids.size()>=500) break;
        }
        if(ids.isEmpty()) throw new ApiException(502,"服务商未返回模型列表，请手动填写模型 ID");
        return List.copyOf(ids);
    }
    @PreDestroy void close() throws Exception { client.close(); }
}
