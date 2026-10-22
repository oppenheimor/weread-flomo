package cn.sparkden.weread;

import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static cn.sparkden.weread.AiModels.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final Accounts accounts;
    private final AiService ai;
    private final AiProviderService providers;
    private final AiTransport transport;
    private final NotebookService notebook;
    public AiController(Accounts accounts,AiService ai,AiProviderService providers,AiTransport transport,NotebookService notebook) { this.accounts=accounts; this.ai=ai; this.providers=providers; this.transport=transport; this.notebook=notebook; }
    private UUID user(Principal principal) { return accounts.id(principal.getName()); }
    @GetMapping("/settings") public Settings settings(Principal principal) { return providers.settings(user(principal)); }
    @PostMapping("/providers") public Settings save(Principal principal,@RequestBody ProviderChange body) { return providers.save(user(principal),body); }
    @PostMapping("/default") public Settings choose(Principal principal,@RequestBody DefaultModel body) { return providers.setDefault(user(principal),body); }
    public record ProviderId(String id) {}
    @PostMapping("/providers/delete") public Settings delete(Principal principal,@RequestBody ProviderId body) { providers.delete(user(principal),body.id()); return providers.settings(user(principal)); }
    @PostMapping("/models") public Map<String,Object> discover(Principal principal,@RequestBody ProviderId body) { return Map.of("models",transport.discover(providers.connection(user(principal),body.id(),"discovery"))); }
    @PostMapping("/test") public Map<String,Object> test(Principal principal,@RequestBody DefaultModel body) {
        String response=transport.generate(providers.connection(user(principal),body.providerId(),body.model()),"Respond briefly.","Reply OK",1024);
        if(response.isBlank()) throw new ApiException(502,"模型返回空内容，连接测试未通过");
        return Map.of("ok",true);
    }
    @GetMapping("/chapters") public List<Models.Chapter> chapters(Principal principal,@RequestParam String bookId) { return ai.chapters(user(principal),bookId); }
    @GetMapping("/chapter") public ChapterState chapter(Principal principal,@RequestParam String bookId,@RequestParam String chapterId) { return ai.state(user(principal),bookId,chapterId); }
    @PostMapping("/start") public Object start(Principal principal,@RequestBody Start body) { return ai.start(user(principal),body); }
    @PostMapping("/browser/progress") public ChapterState browserProgress(Principal principal,@RequestBody BrowserResult body) { return ai.browserResult(user(principal),body,true); }
    @PostMapping("/browser/complete") public ChapterState browserComplete(Principal principal,@RequestBody BrowserResult body) { return ai.browserResult(user(principal),body,false); }
    @PostMapping("/edit") public ChapterState edit(Principal principal,@RequestBody Update body) { return ai.update(user(principal),body); }
    @PostMapping("/send") public Map<String,Object> send(Principal principal,@RequestBody Selection body) { return ai.send(user(principal),body); }
    @PostMapping("/resolve") public ChapterState resolve(Principal principal,@RequestBody Resolution body) { return ai.resolve(user(principal),body); }
    public record PreviewId(String previewId) {}
    @PostMapping("/refresh-preview") public Map<String,Object> refresh(Principal principal,@RequestBody PreviewId body) { return Map.of("notes",notebook.refreshPreview(user(principal),body.previewId())); }
}
