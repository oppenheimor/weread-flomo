package cn.sparkden.weread;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Reads public answer text only. Vendor reasoning/thinking fields are never forwarded. */
final class AiStream {
    interface Listener extends Consumer<String> { void phase(String phase); }
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AiStream.class);
    private static final int MAX_BYTES=2_000_000;
    private static final int MAX_WIRE_BYTES=32_000_000;
    static String read(InputStream input,String protocol,ObjectMapper mapper,Consumer<String> onText) throws IOException {
        var limited=new FilterInputStream(input) {
            int count;
            private void check(int n) {
                if(n<=0) return;
                int before=count; count+=n;
                if(before<=MAX_BYTES && count>MAX_BYTES) log.info("AI SSE wire exceeded 2 MB; envelopes/thinking are separate from public answer limit");
                if(count>MAX_WIRE_BYTES) throw new ApiException(502,"AI 流式传输超过 32 MB 上限，旧结果未改动");
            }
            @Override public int read() throws IOException { int b=super.read(); check(b<0?0:1); return b; }
            @Override public int read(byte[] b,int off,int len) throws IOException { int n=in.read(b,off,len); check(n); return n; }
        };
        var reader=new BufferedReader(new InputStreamReader(limited,StandardCharsets.UTF_8));
        var answer=new StringBuilder(); var data=new StringBuilder();
        int publicBytes=0;
        boolean finished=false,thinkingReported=false; long deadline=System.nanoTime()+java.time.Duration.ofMinutes(4).toNanos();
        String line;
        while((line=reader.readLine())!=null) {
            if(System.nanoTime()>deadline) throw new ApiException(502,"AI 输出时间过长，已停止，请重试");
            if(line.length()>MAX_BYTES) throw new ApiException(502,"AI 单个流式事件过大");
            if(!line.isEmpty()) { if(line.startsWith("data:")) { if(!data.isEmpty()) data.append('\n'); data.append(line.substring(5).stripLeading()); if(data.length()>MAX_BYTES) throw new ApiException(502,"AI 单个流式事件过大"); } continue; }
            if(data.isEmpty()) continue;
            String event=data.toString(); data.setLength(0);
            if(event.equals("[DONE]")) break;
            JsonNode value=mapper.readTree(event);
            if(value.has("error") || value.path("type").asText().equals("error")) throw new ApiException(502,"AI 流式响应报告错误，旧结果未改动");
            boolean thinking = !value.path("choices").path(0).path("delta").path("reasoning_content").asText("").isEmpty()
                || value.path("delta").path("type").asText().equals("thinking_delta");
            for(var part:value.path("candidates").path(0).path("content").path("parts")) thinking |= part.path("thought").asBoolean();
            if(thinking && !thinkingReported && answer.isEmpty()) {
                thinkingReported=true;
                if(onText instanceof Listener listener) listener.phase("thinking");
            }
            String delta="";
            switch(protocol) {
                case "anthropic" -> {
                    String type=value.path("type").asText();
                    if(type.equals("content_block_delta") && value.path("delta").path("type").asText().equals("text_delta")) delta=value.path("delta").path("text").asText("");
                    if(type.equals("message_delta") && !value.path("delta").path("stop_reason").isMissingNode()) {
                        String reason=value.path("delta").path("stop_reason").asText();
                        if(!reason.equals("end_turn") && !reason.equals("stop_sequence")) throw new ApiException(502,"AI 输出被截断或未正常完成");
                        finished=true;
                    }
                }
                case "google" -> {
                    var candidate=value.path("candidates").path(0); var text=new StringBuilder();
                    for(var part:candidate.path("content").path("parts")) if(!part.path("thought").asBoolean()) text.append(part.path("text").asText(""));
                    delta=text.toString();
                    if(candidate.hasNonNull("finishReason")) { if(!candidate.path("finishReason").asText().equals("STOP")) throw new ApiException(502,"AI 输出被截断或未正常完成"); finished=true; }
                }
                default -> {
                    var choice=value.path("choices").path(0);
                    delta=choice.path("delta").path("content").asText("");
                    if(choice.hasNonNull("finish_reason")) { if(!choice.path("finish_reason").asText().equals("stop")) throw new ApiException(502,"AI 输出被截断或未正常完成"); finished=true; }
                }
            }
            if(!delta.isEmpty()) {
                publicBytes+=delta.getBytes(StandardCharsets.UTF_8).length;
                if(publicBytes>MAX_BYTES) throw new ApiException(502,"AI 正文超过 2 MB 上限，旧结果未改动");
                answer.append(delta); onText.accept(answer.toString());
            }
        }
        if(!finished || answer.isEmpty()) throw new ApiException(502,"AI 输出中断或为空，旧结果未改动，请重试");
        log.info("AI SSE completed: wireBytes={}, publicBytes={}",limited.count,publicBytes);
        return answer.toString();
    }
}
