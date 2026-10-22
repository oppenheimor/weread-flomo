package cn.sparkden.weread;

import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShareController {
    private final Accounts accounts;
    private final ShareService shares;
    public ShareController(Accounts accounts, ShareService shares) { this.accounts=accounts; this.shares=shares; }
    public record Change(String bookId, Boolean enabled) {}
    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow, noarchive").body(body);
    }
    @GetMapping("/api/shares") public ResponseEntity<ShareService.State> state(Principal user,@RequestParam(required=false) String bookId) {
        return response(shares.state(accounts.id(user.getName()),bookId));
    }
    @PostMapping("/api/shares") public ResponseEntity<ShareService.State> change(Principal user,@RequestBody Change body) {
        if(body.enabled()==null) throw new ApiException(400,"请选择分享状态");
        var owner=accounts.id(user.getName());
        return response(body.enabled()?shares.enable(owner,body.bookId()):shares.disable(owner,body.bookId()));
    }
    @GetMapping("/api/public/shares/{token}") public ResponseEntity<?> view(@PathVariable String token,@RequestParam(required=false) String bookId) {
        try { return response(shares.view(token,bookId)); }
        catch(ApiException e) {
            var builder=ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow, noarchive");
            if(e.status()==429) builder.header("Retry-After","60");
            return builder.body(java.util.Map.of("error",e.getMessage()));
        }
    }
}
