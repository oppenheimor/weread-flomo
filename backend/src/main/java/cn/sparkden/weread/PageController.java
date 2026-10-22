package cn.sparkden.weread;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public article routes only; API and all other paths keep their existing security rules. */
@Controller
public class PageController {
    private final ShareService shares;
    public PageController(ShareService shares) { this.shares=shares; }
    @GetMapping({"/share", "/share/{*path}"})
    public String share(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String token=request.getRequestURI().substring(request.getContextPath().length()).replaceFirst("^/share/?", "").replaceFirst("/$", "");
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");
        if(!shares.exists(token)) response.setStatus(404);
        return "forward:/index.html";
    }
    @GetMapping({"/introduction", "/introduction/"})
    public String introduction() {
        return "forward:/index.html";
    }
}
