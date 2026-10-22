package cn.sparkden.weread;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static cn.sparkden.weread.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.admin-username=testowner", "app.admin-password=Test-only-Password-2026", "app.import-user=testowner",
    "app.credential-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "app.public-origin=http://127.0.0.1:18081", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL",matches=".+")
class SharePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username",() -> "test"); registry.add("spring.datasource.password",() -> "test-only");
    }
    @Autowired ShareService shares;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired Ledger ledger;
    @Autowired Normalizer normalizer;
    @MockitoBean RemoteApis remote;
    // The existing PostgresTest context holds the singleton app lock in this test JVM.
    @MockitoBean InstanceGuard guard;
    UUID owner; String username;
    Book first=new Book("b1","一本书","作者",1,1), second=new Book("b2","另一本书","作者",0,0);
    SourceNotes source=new SourceNotes(List.of(new Highlight("h","原始划线","c","1-2",1L)),List.of(new Review("r","原始想法","","c","1-2",2L)),List.of(new Chapter("c","第一章",1)));
    @BeforeEach void setup() {
        owner=UUID.randomUUID(); username="share-test-"+owner;
        db.update("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)",owner,username,"unused");
        when(remote.status(owner)).thenReturn(Map.of("wereadConfigured",true));
        when(remote.books(owner)).thenReturn(List.of(first,second));
        when(remote.notes(owner,"b1")).thenReturn(source);
        when(remote.notes(owner,"b2")).thenReturn(new SourceNotes(List.of(),List.of()));
    }
    @Test void linksAreIndependentIdempotentRevocableAndAccountScoped() {
        String shelf=shares.enable(owner,null).token(), book=shares.enable(owner,"b1").token();
        assertTrue(shelf.matches("[A-Za-z0-9_-]{43}")); assertNotEquals(shelf,book);
        assertEquals(shelf,shares.enable(owner,null).token());
        UUID other=UUID.randomUUID(); assertFalse(shares.state(other,null).enabled());
        shares.disable(other,null); assertTrue(shares.exists(shelf));
        shares.disable(owner,"b1"); assertFalse(shares.exists(book)); assertTrue(shares.exists(shelf));
        String replacement=shares.enable(owner,"b1").token(); assertNotEquals(book,replacement); assertFalse(shares.exists(book));
        shares.disable(owner,null); assertTrue(shares.exists(replacement));
        assertEquals(404,assertThrows(ApiException.class,() -> shares.view(shelf,null)).status());
    }
    @Test void singleBookCannotReadAnotherBookAndUsesOwnersOriginalData() throws Exception {
        var note=normalizer.normalize(first,source).notes().getFirst();
        ledger.reserve(owner,note); ledger.finish(owner,note.id(),"sent");
        ledger.dismiss(owner,note); ledger.saveThought(owner,note.id(),"仅在同步工作台编辑的私有文字");
        String token=shares.enable(owner,"b1").token();
        var view=shares.view(token,null);
        assertEquals("book",view.scope()); assertTrue(view.books().isEmpty()); assertNull(view.username());
        assertEquals("原始想法",view.detail().notes().getFirst().thought());
        assertEquals(404,assertThrows(ApiException.class,() -> shares.view(token,"b2")).status());
        verify(remote,never()).notes(owner,"b2");
        mvc.perform(get("/api/public/shares/"+token)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.scope").value("book"))
            .andExpect(jsonPath("$.detail.notes[0].thought").value("原始想法"))
            .andExpect(jsonPath("$.detail.notes[0].status").doesNotExist())
            .andExpect(jsonPath("$.detail.notes[0].sourceKeys").doesNotExist())
            .andExpect(jsonPath("$.owner").doesNotExist());
        verify(remote,never()).send(any(),any());
    }
    @Test void refreshReadsNewBooksAndNotesWithoutSnapshots() {
        String token=shares.enable(owner,null).token();
        assertEquals(2,shares.view(token,null).books().size());
        assertEquals(username,shares.view(token,null).username());
        when(remote.books(owner)).thenReturn(List.of(first));
        assertEquals(1,shares.view(token,null).books().size());
        assertEquals("shelf",shares.view(token,"b1").scope());
        when(remote.notes(owner,"b1")).thenReturn(new SourceNotes(List.of(),List.of()));
        assertTrue(shares.view(token,"b1").detail().notes().isEmpty());
    }
    @Test void upstreamFailureIsNot404OrCredentialLeakAndCloseStillWorks() throws Exception {
        String token=shares.enable(owner,null).token();
        when(remote.books(owner)).thenThrow(new ApiException(400,"SECRET: key invalid"));
        mvc.perform(get("/api/public/shares/"+token)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("暂时无法加载，请稍后再试"));
        shares.disable(owner,null); assertFalse(shares.exists(token));
        mvc.perform(get("/api/public/shares/"+token)).andExpect(status().isNotFound());
    }
    @Test void closingDuringUpstreamFetchDoesNotLeakResult() {
        String token=shares.enable(owner,null).token();
        when(remote.books(owner)).thenAnswer(call -> { shares.disable(owner,null); return List.of(first); });
        assertEquals(404,assertThrows(ApiException.class,() -> shares.view(token,null)).status());
    }
    @Test void anonymousReadsAreBoundedAndInvalidTokensDoNotCallUpstream() {
        assertEquals(404,assertThrows(ApiException.class,() -> shares.view("invalid",null)).status());
        verifyNoInteractions(remote);
        String token=shares.enable(owner,null).token();
        for(int i=0;i<60;i++) shares.view(token,null);
        assertEquals(429,assertThrows(ApiException.class,() -> shares.view(token,null)).status());
        assertTrue(new ShareService(db,remote,normalizer).exists(token)); // Persists beyond service lifetime.
    }
    @Test void concurrentEnableReturnsOneStableLink() throws Exception {
        try(var executor=Executors.newFixedThreadPool(4)) {
            var jobs=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++) jobs.add(executor.submit(() -> shares.enable(owner,null).token()));
            var tokens=new HashSet<String>(); for(var job:jobs) tokens.add(job.get(10,TimeUnit.SECONDS));
            assertEquals(1,tokens.size());
        }
    }
    @Test void managementRequiresLoginCsrfAndSameOrigin() throws Exception {
        mvc.perform(get("/api/shares")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shares").with(user(username)).header("Origin","http://127.0.0.1:18081")
            .contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/shares").with(user(username)).with(csrf()).header("Origin","https://stranger.test")
            .contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/shares").with(user(username)).with(csrf()).header("Origin","http://127.0.0.1:18081")
            .contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/api/public/shares/not-a-token")).andExpect(status().isNotFound());
        mvc.perform(get("/share/not-a-token")).andExpect(status().isNotFound()).andExpect(forwardedUrl("/index.html"));
    }
}
