package cn.sparkden.weread;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.sparkden.weread.Models.*;

@Service
public class RemoteApis {
    private final Credentials credentials;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private long lastSend;
    public RemoteApis(ObjectMapper mapper, Credentials credentials) {
        this.mapper=mapper; this.credentials=credentials;
    }
    public static boolean validFlomoUrl(String url) {
        return url != null && url.matches("https://flomoapp\\.com/iwh/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/?");
    }
    public Map<String,Object> status(UUID user) { return credentials.status(user); }
    private JsonNode post(String url, Object body, String bearer) {
        try {
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
            if (bearer!=null) request.header("Authorization","Bearer "+bearer);
            var response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()<200 || response.statusCode()>=300) throw new ApiException(502,"远端接口返回 HTTP "+response.statusCode()+"，请核对凭证、权限和额度");
            var result=mapper.readTree(response.body());
            if(result==null || !result.isObject()) throw new ApiException(502,"远端响应格式异常");
            return result;
        } catch (ApiException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(502,"请求中断，请核对远端记录，勿直接重发"); }
        catch (Exception e) { throw new ApiException(502,"远端连接失败、超时或响应无法解析，请核对后再操作"); }
    }
    private JsonNode weread(String wereadKey, String api, Map<String,Object> params) {
        var body=new HashMap<>(params); body.put("api_name",api); body.put("skill_version","1.0.5");
        JsonNode data=post("https://i.weread.qq.com/api/agent/gateway",body,wereadKey);
        if(data.has("upgrade_info")) throw new ApiException(502,"微信读书要求升级接口版本，已停止");
        if(data.has("errcode") && (!data.get("errcode").isIntegralNumber() || data.get("errcode").asLong()!=0)) throw new ApiException(502,"微信读书拒绝请求，请检查凭证和权限");
        return data;
    }
    private static JsonNode array(JsonNode value) {
        if(!value.isArray()) throw new ApiException(502,"微信读书列表字段缺失，已停止拉取"); return value;
    }
    private static String required(JsonNode row,String key) {
        JsonNode value=row.path(key);
        if(!(value.isTextual() || value.isIntegralNumber()) || value.asText().isBlank()) throw new ApiException(502,"微信读书记录不完整，已停止拉取");
        return value.asText();
    }
    private static Integer count(JsonNode row,String field) { return row.path(field).isInt()?row.get(field).asInt():null; }
    public static String coverUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getFragment()!=null) return "";
            if (!Set.of("cdn.weread.qq.com", "wfqqreader-1252317822.image.myqcloud.com").contains(uri.getHost())) return "";
            return uri.toString();
        } catch (Exception e) { return ""; }
    }
    public static Book parseBook(JsonNode row) {
        JsonNode b = row.path("book"), progress = row.path("readingProgress");
        Double value = progress.isNumber() && Double.isFinite(progress.asDouble()) && progress.asDouble() >= 0 ? progress.asDouble() : null;
        return new Book(required(row,"bookId"), required(b,"title"), b.path("author").asText(""), count(row,"noteCount"), count(row,"reviewCount"),
            coverUrl(b.path("cover").asText("")), value, count(row,"markedStatus"));
    }
    private static boolean more(JsonNode data) {
        JsonNode v=data.path("hasMore");
        if(v.isBoolean()) return v.asBoolean();
        if(v.isInt() && (v.asInt()==0 || v.asInt()==1)) return v.asInt()==1;
        throw new ApiException(502,"分页信息不完整，已停止拉取");
    }
    public List<Book> books(UUID user) {
        String wereadKey = credentials.required(user, "weread");
        var result=new LinkedHashMap<String,Book>(); var seen=new HashSet<Long>(); Long cursor=null;
        for(int page=0;page<10000;page++) {
            var params=new HashMap<String,Object>(); params.put("count",100); if(cursor!=null) params.put("lastSort",cursor);
            var data=weread(wereadKey,"/user/notebooks",params); var rows=array(data.path("books"));
            for(var row:rows) {
                Book book = parseBook(row);
                result.put(book.bookId(), book);
            }
            if(!more(data)) return new ArrayList<>(result.values());
            if(rows.isEmpty() || !rows.get(rows.size()-1).path("sort").isIntegralNumber()) throw new ApiException(502,"分页游标缺失");
            cursor=rows.get(rows.size()-1).get("sort").asLong();
            if(!seen.add(cursor)) throw new ApiException(502,"书籍分页游标未前进");
        }
        throw new ApiException(502,"书籍分页超出安全上限");
    }
    static List<Chapter> parseChapters(JsonNode data) {
        var chapters = new LinkedHashMap<String, Chapter>();
        if (data.path("chapters").isArray()) for (var c : data.path("chapters")) {
            String id = c.path("chapterUid").asText(""), title = c.path("title").asText("");
            if (!id.isBlank() && !title.isBlank()) chapters.put(id, new Chapter(id, title,
                c.path("chapterIdx").canConvertToInt() ? c.path("chapterIdx").asInt() : Integer.MAX_VALUE - 1));
        }
        return new ArrayList<>(chapters.values());
    }
    static Review parseReview(JsonNode item) {
        JsonNode content = item.path("content");
        if (!content.isTextual() && !content.isNull() && !content.isMissingNode())
            throw new ApiException(502,"微信读书想法正文格式异常，已停止拉取");
        // 上游可能保留正文为空的想法；交由标准化阶段跳过，不阻断整本书。
        return new Review(required(item,"reviewId"), content.asText(""), item.path("abstract").asText(""),
            item.path("chapterUid").asText(""), item.path("range").asText(""),
            item.path("createTime").asLong(0) > 0 ? item.path("createTime").asLong() : null);
    }
    public SourceNotes notes(UUID user, String bookId) {
        String wereadKey = credentials.required(user, "weread");
        var data=weread(wereadKey,"/book/bookmarklist",Map.of("bookId",bookId));
        var chapters = new LinkedHashMap<String, Chapter>();
        parseChapters(data).forEach(c -> chapters.put(c.id(), c));
        var highlights=new ArrayList<Highlight>();
        for(var h:array(data.path("updated"))) highlights.add(new Highlight(required(h,"bookmarkId"),required(h,"markText"),h.path("chapterUid").asText(""),h.path("range").asText(""),h.path("createTime").asLong(0) > 0 ? h.path("createTime").asLong() : null));
        var reviews=new LinkedHashMap<String,Review>(); var seen=new HashSet<Long>(); seen.add(0L); long cursor=0;
        for(int page=0;page<10000;page++) {
            var r=weread(wereadKey,"/review/list/mine",Map.of("bookid",bookId,"count",100,"synckey",cursor));
            for(var row:array(r.path("reviews"))) {
                var item=row.path("review"); String id=required(item,"reviewId");
                String chapterId = item.path("chapterUid").asText(""), chapterName = item.path("chapterName").asText("");
                if (!chapterId.isBlank() && !chapterName.isBlank()) chapters.putIfAbsent(chapterId, new Chapter(chapterId, chapterName,
                    item.path("chapterIdx").canConvertToInt() ? item.path("chapterIdx").asInt() : Integer.MAX_VALUE - 1));
                reviews.put(id,parseReview(item));
            }
            if(!more(r)) return new SourceNotes(highlights,new ArrayList<>(reviews.values()),new ArrayList<>(chapters.values()));
            if(!r.path("synckey").isIntegralNumber()) throw new ApiException(502,"想法分页游标缺失");
            cursor=r.get("synckey").asLong(); if(!seen.add(cursor)) throw new ApiException(502,"想法分页游标未前进");
        }
        throw new ApiException(502,"想法分页超出安全上限");
    }
    public static boolean saved(JsonNode data) { return data.path("code").isIntegralNumber() && data.get("code").asLong()==0 && data.path("memo").path("slug").isTextual() && !data.path("memo").path("slug").asText().isBlank(); }
    public synchronized void send(UUID user, String content) {
        String flomoUrl = credentials.required(user, "flomo");
        if(!validFlomoUrl(flomoUrl)) throw new ApiException(400,"请在账号设置中配置有效的 flomo API 地址");
        long wait=1100-(System.currentTimeMillis()-lastSend);
        if(wait>0) try { Thread.sleep(wait); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(502,"发送中断"); }
        lastSend=System.currentTimeMillis();
        var response=post(flomoUrl,Map.of("content",content,"content_type","markdown"),null);
        if(!saved(response)) throw new ApiException(502,"未取得 flomo 成功回执，本批次已停止；请先到 flomo 核对");
    }
}
