package cn.sparkden.weread;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public article routes only; API and all other paths keep their existing security rules. */
@Controller
public class PageController {
    @GetMapping({"/introduction", "/introduction/"})
    public String introduction() {
        return "forward:/index.html";
    }
}
