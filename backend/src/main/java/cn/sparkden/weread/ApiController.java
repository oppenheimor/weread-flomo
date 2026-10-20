package cn.sparkden.weread;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import static cn.sparkden.weread.Models.*;

@RestController
public class ApiController {
    private final Accounts accounts;
    private final NotebookService service;
    private final RemoteApis remote;
    public ApiController(Accounts accounts,NotebookService service,RemoteApis remote) { this.accounts=accounts; this.service=service; this.remote=remote; }
    public record Registration(String username, String password) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    @PostMapping("/api/auth/register") public ResponseEntity<Map<String,Boolean>> register(Principal principal, @RequestBody Registration body) {
        if (principal != null) throw new ApiException(400,"请先退出当前账号");
        accounts.register(body.username(), body.password());
        return ResponseEntity.status(201).body(Map.of("ok",true));
    }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","ready"); }
    @GetMapping("/api/auth/session") public Map<String,Object> session(Principal principal,HttpServletRequest req) {
        CsrfToken token=(CsrfToken) req.getAttribute(CsrfToken.class.getName());
        return Map.of("authenticated",principal!=null,"username",principal==null?"":principal.getName(),"csrfToken",token.getToken());
    }
    @GetMapping({"/api/status", "/api/account/credentials"}) public Map<String,Object> status(Principal user) { return remote.status(accounts.id(user.getName())); }
    @PostMapping("/api/account/credentials") public Map<String,Object> saveCredentials(Principal user,@RequestBody Credentials.Change body) {
        return service.saveCredentials(accounts.id(user.getName()),body);
    }
    public record CredentialSelection(String provider) {}
    // Explicit, authenticated, CSRF-protected reveal; ordinary status/save responses never expose secrets.
    @PostMapping("/api/account/credentials/reveal") public ResponseEntity<Credentials.Revealed> revealCredential(
            Principal user, @RequestBody CredentialSelection body) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .header("Pragma", "no-cache")
            .body(service.revealCredential(accounts.id(user.getName()), body.provider()));
    }
    public record DeleteCredential(String provider) {}
    @PostMapping("/api/account/credentials/delete") public Map<String,Object> deleteCredentials(Principal user,@RequestBody DeleteCredential body) {
        if(body.provider()==null) throw new ApiException(400,"请选择凭证类型");
        return service.deleteCredentials(accounts.id(user.getName()),body.provider());
    }
    @PostMapping("/api/books") public Map<String,Object> books(Principal user) {
        UUID id=accounts.id(user.getName());
        return Map.of("books",service.books(id),"sync",service.syncStats(id));
    }
    public record BookRequest(String bookId) {}
    @PostMapping("/api/preview") public PreviewResponse preview(Principal user,@RequestBody BookRequest body) {
        if(body.bookId()==null || body.bookId().length()>100) throw new ApiException(400,"书籍 ID 无效");
        return service.preview(accounts.id(user.getName()),body.bookId());
    }
    @PostMapping("/api/send") public SendResult send(Principal user,@RequestBody SendRequest body) { return service.send(accounts.id(user.getName()),body); }
    @GetMapping("/api/sync") public Map<String,Object> sync(Principal user) { return Map.of("sync",service.syncStats(accounts.id(user.getName()))); }
    @PostMapping("/api/notes/thought") public Map<String,Object> saveThought(Principal user,@RequestBody ThoughtRequest body) {
        return Map.of("notes",service.saveThought(accounts.id(user.getName()),body));
    }
    @PostMapping("/api/notes/delete") public Map<String,Object> deleteNote(Principal user,@RequestBody DeleteRequest body) {
        return Map.of("notes",service.delete(accounts.id(user.getName()),body));
    }
    @PostMapping("/api/resolve") public Map<String,Object> resolve(Principal user,@RequestBody ResolveRequest body) {
        return Map.of("notes",service.resolve(accounts.id(user.getName()),body));
    }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(ApiException.class) ResponseEntity<Map<String,String>> api(ApiException e) { return ResponseEntity.status(e.status()).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String,String>> malformed() { return ResponseEntity.badRequest().body(Map.of("error","请求格式错误")); }
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> other(Exception e) {
        // 不返回 JDBC、HTTP 或配置异常中的潜在密钥。
        return ResponseEntity.internalServerError().body(Map.of("error","服务处理失败；若刚才在发送，请重新拉取核对状态，不要直接重发"));
    }
}
