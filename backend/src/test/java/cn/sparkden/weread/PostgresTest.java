package cn.sparkden.weread;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static cn.sparkden.weread.Models.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.admin-username=testowner", "app.admin-password=Test-only-Password-2026", "app.import-user=testowner",
    "app.credential-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.public-origin=http://127.0.0.1:18081", "server.servlet.session.cookie.secure=false"})
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL",matches=".+")
class PostgresTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username",() -> "test"); registry.add("spring.datasource.password",() -> "test-only");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired Ledger ledger;
    @Autowired PasswordEncoder encoder;
    @Autowired LedgerImport importer;
    @Autowired Credentials credentials;
    @Autowired AiProviderService aiProviders;
    @Autowired BookPositionController positions;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    private UUID aiUser() {
        UUID user=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,username,password_hash) VALUES (?,?,?)",user,"ai-"+user,encoder.encode("test-only-password")); return user;
    }
    private AiModels.Snapshot aiSnapshot() {
        var chapter=new Chapter("c","前言",1);
        return new AiModels.Snapshot(new Book("b","测试书","作者",2,0),chapter,List.of(
            new Note("a","b","highlight",List.of("source-a"),"原文 A","原文 A","想法 A",chapter,1L),
            new Note("b","b","highlight",List.of("source-b"),"原文 B","原文 B","",chapter,2L)));
    }
    private AiService fixtureAi(AiTransport transport,RemoteApis remote) {
        NotebookService notebook=org.mockito.Mockito.mock(NotebookService.class);
        org.mockito.Mockito.when(notebook.exclusive(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(0)).get());
        org.mockito.Mockito.when(notebook.serialized(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(0)).get());
        org.mockito.Mockito.when(notebook.aiSnapshot(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(aiSnapshot());
        return new AiService(db,mapper,notebook,ledger,remote,aiProviders,transport,new AiOrganizer(mapper),transactions);
    }
    private void aiConfig(UUID user) {
        aiProviders.save(user,new AiModels.ProviderChange("test","Test","openai","https://api.example.com/v1","secret-test-key",List.of("test-model")));
        aiProviders.setDefault(user,new AiModels.DefaultModel("test","test-model"));
    }
    private AiModels.ChapterState waitAi(AiService service,UUID user) throws Exception {
        for(int i=0;i<100;i++) { var state=service.state(user,"b","c"); if(!state.state().equals("running")) return state; Thread.sleep(20); }
        fail("AI job did not complete"); return null;
    }
    @Test void bookPositionsPersistIndependentlyByAccountAndBook() {
        UUID user=aiUser(),other=aiUser();
        java.security.Principal owner=() -> "ai-"+user, stranger=() -> "ai-"+other;
        assertEquals("",positions.get(owner,"book-a").get("chapterId"));
        positions.save(owner,new BookPositionController.Position("book-a","chapter-2"));
        positions.save(owner,new BookPositionController.Position("book-b","chapter-5"));
        positions.save(owner,new BookPositionController.Position("book-a","chapter-3"));
        assertEquals("chapter-3",positions.get(owner,"book-a").get("chapterId"));
        assertEquals("chapter-5",positions.get(owner,"book-b").get("chapterId"));
        assertEquals("",positions.get(stranger,"book-a").get("chapterId"));
        assertThrows(ApiException.class,() -> positions.save(owner,new BookPositionController.Position("book-a","")));
    }
    @Test void aiCredentialsAreEncryptedIsolatedAndDefaultIsExplicit() {
        UUID user=aiUser(),other=aiUser(); aiConfig(user);
        var state=aiProviders.settings(user);
        assertTrue(state.providers().getFirst().hasKey()); assertEquals("test-model",state.defaultModel().model());
        assertFalse(db.queryForObject("SELECT ciphertext FROM ai_provider WHERE user_id=?",String.class,user).contains("secret-test-key"));
        assertTrue(aiProviders.settings(other).providers().isEmpty()); assertNull(aiProviders.settings(other).defaultModel());
        assertThrows(ApiException.class,() -> aiProviders.connection(other,"test","test-model"));
        aiProviders.save(user,new AiModels.ProviderChange("test","Renamed","openai","https://api.example.com/v1",null,List.of("test-model")));
        assertEquals("secret-test-key",aiProviders.defaultConnection(user).key());
        aiProviders.delete(user,"test"); assertNull(aiProviders.settings(user).defaultModel());
    }
    @Test void browserMagpieJobsValidateIsolateExpireAndNeverUseServerTransport() {
        UUID user=aiUser(),other=aiUser();
        aiProviders.save(user,new AiModels.ProviderChange("magpie","Magpie","openai","http://localhost:3425/v1","saved-gateway-key",List.of()));
        assertEquals("saved-gateway-key",aiProviders.magpieKey(user));
        assertThrows(ApiException.class,() -> aiProviders.magpieKey(other));
        assertNotEquals("saved-gateway-key",db.queryForObject("SELECT ciphertext FROM ai_provider WHERE user_id=? AND id='magpie'",String.class,user));
        aiProviders.save(user,new AiModels.ProviderChange("magpie","Magpie","openai","http://localhost:3425/v1",null,List.of("provider/model")));
        aiProviders.setDefault(user,new AiModels.DefaultModel("magpie","provider/model"));
        assertTrue(aiProviders.settings(user).providers().getFirst().hasKey());
        assertEquals("saved-gateway-key",aiProviders.magpieKey(user));
        assertThrows(ApiException.class,() -> aiProviders.defaultConnection(user));
        var transport=org.mockito.Mockito.mock(AiTransport.class);
        var service=fixtureAi(transport,org.mockito.Mockito.mock(RemoteApis.class));
        String valid="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"标题\",\"body\":\"正文\"}]}";
        try {
            var job=(AiModels.BrowserJob)service.start(user,new AiModels.Start("preview","c",false));
            assertEquals("provider/model",job.model()); assertTrue(job.prompt().contains("原文 A"));
            var result=new AiModels.BrowserResult("b","c",job.jobId(),valid,false);
            assertThrows(ApiException.class,() -> service.browserResult(other,result,false));
            assertThrows(ApiException.class,() -> service.browserResult(user,new AiModels.BrowserResult("b","c",UUID.randomUUID(),valid,false),false));
            assertEquals("running",service.browserResult(user,result,true).state());
            assertFalse(service.state(user,"b","c").drafts().isEmpty());
            var saved=service.browserResult(user,result,false);
            assertEquals("ready",saved.state()); assertEquals(1,saved.notes().size());
            assertEquals(saved.notes().getFirst().id(),service.browserResult(user,result,false).notes().getFirst().id());
            var replacement=(AiModels.BrowserJob)service.start(user,new AiModels.Start("preview","c",true));
            assertThrows(ApiException.class,() -> service.browserResult(user,result,false));
            assertThrows(ApiException.class,() -> service.browserResult(user,new AiModels.BrowserResult("b","c",replacement.jobId(),"{\"groups\":[]}",false),false));
            assertEquals("failed",service.state(user,"b","c").state());
            assertEquals(saved.notes().getFirst().id(),service.state(user,"b","c").notes().getFirst().id());
            var expired=(AiModels.BrowserJob)service.start(user,new AiModels.Start("preview","c",true));
            db.update("UPDATE ai_chapter SET client_deadline=now()-interval '1 second' WHERE user_id=?",user);
            assertEquals("failed",service.state(user,"b","c").state());
            assertThrows(ApiException.class,() -> service.browserResult(user,new AiModels.BrowserResult("b","c",expired.jobId(),valid,false),false));
            org.mockito.Mockito.verifyNoInteractions(transport);
        } finally { service.stop(); }
    }
    @Autowired StatisticsController statistics;
    @Test void statisticsCountOriginalSourcesAcrossAiAndBeijingMidnight() {
        UUID user=aiUser();
        var chapter=new Chapter("c","章",1);
        var first=new Note("s1","b1","highlight",List.of("[\"b1\",\"highlight\",\"1\"]","[\"b1\",\"review\",\"2\"]"),"内容","原文","想法",chapter,1L);
        var second=new Note("s2","b2","highlight",List.of("[\"b2\",\"highlight\",\"3\"]"),"内容","原文","",chapter,1L);
        ledger.reserve(user,first); ledger.finish(user,first.id(),"sent");
        db.update("UPDATE delivery SET updated_at='2024-02-29T15:59:59Z' WHERE user_id=? AND note_id=?",user,first.id());
        ledger.reserveOrganized(user,"ai:stats","整理",List.of(first,second));
        assertEquals(2,statistics.forUser(user).notes()); // sending sources do not count
        ledger.finish(user,"ai:stats","uncertain");
        assertEquals(2,statistics.forUser(user).notes());
        ledger.resolveOrganized(user,"ai:stats",true);
        db.update("UPDATE delivery SET updated_at='2024-02-29T16:00:00Z' WHERE user_id=? AND note_id='ai:stats'",user);
        ledger.reserveOrganized(user,"ai:repeat","再次整理",List.of(first,second)); ledger.finish(user,"ai:repeat","sent");
        var result=statistics.forUser(user);
        assertEquals(3,result.notes()); assertEquals(2,result.books()); assertEquals(2,result.activeDays());
        assertEquals(Map.of("2024-02-29",2L,"2024-03-01",1L),result.days());
        assertEquals(0,statistics.forUser(aiUser()).notes());
    }
    @Test void organizedDeliveryCanFollowOriginalAndPreservesItsSentHistory() {
        UUID user=aiUser(); var sources=aiSnapshot().notes();
        ledger.reserve(user,sources.getFirst()); ledger.finish(user,sources.getFirst().id(),"sent");
        ledger.reserveOrganized(user,"ai:test","整理正文",sources);
        assertEquals(1,ledger.sentCount(user,List.of(sources.getFirst().id(),"ai:test")));
        assertEquals(0,ledger.sentCount(aiUser(),List.of(sources.getFirst().id(),"ai:test")));
        assertThrows(ApiException.class,() -> ledger.sentCount(user,List.of()));
        assertThrows(ApiException.class,() -> ledger.sentCount(user,List.of("ai:test","ai:test")));
        assertEquals("sent",ledger.status(user,sources.getFirst()));
        assertEquals("sending",ledger.status(user,sources.get(1)));
        ledger.finish(user,"ai:test","uncertain");
        assertEquals(1,ledger.sentCount(user,List.of(sources.getFirst().id(),"ai:test")));
        assertThrows(ApiException.class,() -> ledger.reserve(user,sources.get(1)));
        ledger.resolveOrganized(user,"ai:test",false);
        assertEquals("sent",ledger.status(user,sources.getFirst())); assertEquals("ready",ledger.status(user,sources.get(1)));
        ledger.reserveOrganized(user,"ai:test","整理正文",sources); ledger.finish(user,"ai:test","sent");
        assertEquals(2,ledger.sentCount(user,List.of(sources.getFirst().id(),"ai:test")));
        assertEquals("sent",ledger.status(user,sources.get(1)));
        assertThrows(ApiException.class,() -> ledger.reserveOrganized(user,"ai:test","重复",sources));
        assertEquals("ready",ledger.ownStatus(aiUser(),"ai:test"));
    }
    @Test void aiGenerationReplacementValidationEditingAndDeliveryArePersistent() throws Exception {
        UUID user=aiUser(); aiConfig(user);
        var transport=org.mockito.Mockito.mock(AiTransport.class); var remote=org.mockito.Mockito.mock(RemoteApis.class);
        org.mockito.Mockito.when(remote.status(user)).thenReturn(Map.of("sendingEnabled",true));
        String valid="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"标题\",\"body\":\"整理正文\"}]}";
        org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenReturn(valid);
        AiService service=fixtureAi(transport,remote);
        try {
            service.start(user,new AiModels.Start("preview","c",false)); var state=waitAi(service,user);
            assertEquals("ready",state.state()); assertEquals(1,state.notes().size());
            var card=state.notes().getFirst();
            assertTrue(service.state(aiUser(),"b","c").notes().isEmpty());
            service.update(user,new AiModels.Update("b","c",card.id(),"手动标题","手动正文",card.version()));
            assertThrows(ApiException.class,() -> service.update(user,new AiModels.Update("b","c",card.id(),"旧版本","正文",card.version())));
            assertThrows(ApiException.class,() -> service.start(user,new AiModels.Start("preview","c",false)));
            org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenReturn("{\"groups\":[]}");
            service.start(user,new AiModels.Start("preview","c",true)); state=waitAi(service,user);
            assertEquals("failed",state.state()); assertEquals("手动正文",state.notes().getFirst().body());
            service.send(user,new AiModels.Selection("b","c",List.of(card.id()),true));
            assertEquals("sent",service.state(user,"b","c").notes().getFirst().status());
            for(var source:aiSnapshot().notes()) assertEquals("sent",ledger.status(user,source));
            org.mockito.Mockito.verify(remote).send(org.mockito.ArgumentMatchers.eq(user),org.mockito.ArgumentMatchers.contains("手动正文"));
        } finally { service.stop(); }
    }
    @Test void liveDraftsAreIsolatedReadOnlyAndDiscardedOnFailureWithoutReplacingSavedNotes() throws Exception {
        UUID user=aiUser(); aiConfig(user);
        var transport=org.mockito.Mockito.mock(AiTransport.class); var remote=org.mockito.Mockito.mock(RemoteApis.class);
        String valid="{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"旧标题\",\"body\":\"旧正文\"}]}";
        org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenReturn(valid);
        AiService service=fixtureAi(transport,remote);
        var published=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        try {
            service.start(user,new AiModels.Start("preview","c",false)); var saved=waitAi(service,user).notes().getFirst();
            org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
                java.util.function.Consumer<String> callback=call.getArgument(4);
                callback.accept("{\"groups\":[{\"sourceIds\":[\"0\",\"1\"],\"title\":\"新标题\",\"body\":\"正在写");
                published.countDown(); release.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return "{\"groups\":[]}";
            });
            service.start(user,new AiModels.Start("preview","c",true));
            assertTrue(published.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var running=service.state(user,"b","c");
            assertEquals("running",running.state()); assertTrue(running.notes().isEmpty());
            assertEquals("正在写",running.drafts().getFirst().body());
            assertTrue(service.state(aiUser(),"b","c").drafts().isEmpty());
            assertThrows(ApiException.class,() -> service.send(user,new AiModels.Selection("b","c",List.of(saved.id()),true)));
            assertThrows(ApiException.class,() -> service.update(user,new AiModels.Update("b","c","draft-0","x","x",1)));
            release.countDown(); var failed=waitAi(service,user);
            assertEquals("failed",failed.state()); assertTrue(failed.drafts().isEmpty());
            assertEquals(saved.id(),failed.notes().getFirst().id()); assertEquals("旧正文",failed.notes().getFirst().body());
            org.mockito.Mockito.verifyNoInteractions(remote);
        } finally { release.countDown(); service.stop(); }
    }
    @Test void organizedSendStopsOnUncertainAndLocksOnlyItsSources() throws Exception {
        UUID user=aiUser(); aiConfig(user);
        var transport=org.mockito.Mockito.mock(AiTransport.class); var remote=org.mockito.Mockito.mock(RemoteApis.class);
        org.mockito.Mockito.when(remote.status(user)).thenReturn(Map.of("sendingEnabled",true));
        org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenReturn("{\"groups\":[{\"sourceIds\":[\"0\"]},{\"sourceIds\":[\"1\"]}]}");
        org.mockito.Mockito.doThrow(new ApiException(502,"timeout")).when(remote).send(org.mockito.ArgumentMatchers.eq(user),org.mockito.ArgumentMatchers.anyString());
        AiService service=fixtureAi(transport,remote);
        try {
            service.start(user,new AiModels.Start("preview","c",false)); var state=waitAi(service,user);
            var ids=state.notes().stream().map(AiModels.Card::id).toList();
            service.send(user,new AiModels.Selection("b","c",ids,true)); state=service.state(user,"b","c");
            assertEquals("uncertain",state.notes().get(0).status()); assertEquals("ready",state.notes().get(1).status());
            assertEquals("uncertain",ledger.status(user,aiSnapshot().notes().get(0))); assertEquals("ready",ledger.status(user,aiSnapshot().notes().get(1)));
            org.mockito.Mockito.verify(remote,org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.eq(user),org.mockito.ArgumentMatchers.anyString());
            assertThrows(ApiException.class,() -> service.start(user,new AiModels.Start("preview","c",true)));
            service.resolve(user,new AiModels.Resolution("b","c",ids.getFirst(),true));
            assertEquals("sent",service.state(user,"b","c").notes().getFirst().status());
            // A successful replacement retains the immutable sent history.
            service.start(user,new AiModels.Start("preview","c",true)); state=waitAi(service,user);
            assertTrue(state.notes().stream().anyMatch(n -> n.id().equals(ids.getFirst()) && n.status().equals("sent")));
        } finally { service.stop(); }
    }
    @Test void runningAiLocksEditsAndDuplicateJobsButDoesNotDependOnPageLifetime() throws Exception {
        UUID user=aiUser(); aiConfig(user);
        var latch=new java.util.concurrent.CountDownLatch(1);
        var transport=org.mockito.Mockito.mock(AiTransport.class); var remote=org.mockito.Mockito.mock(RemoteApis.class);
        org.mockito.Mockito.when(transport.generateStreaming(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any())).thenAnswer(call -> { latch.await(); return "{\"groups\":[{\"sourceIds\":[\"0\"]},{\"sourceIds\":[\"1\"]}]}"; });
        AiService service=fixtureAi(transport,remote);
        try {
            service.start(user,new AiModels.Start("preview","c",false));
            assertThrows(ApiException.class,() -> service.start(user,new AiModels.Start("preview","c",false)));
            assertThrows(ApiException.class,() -> service.update(user,new AiModels.Update("b","c","unknown","t","b",1)));
            assertThrows(ApiException.class,() -> service.send(user,new AiModels.Selection("b","c",List.of("unknown"),true)));
            latch.countDown(); var state=waitAi(service,user); assertEquals(2,state.notes().size());
            assertEquals("原文 A",AiOrganizer.content(state.notes().getFirst()));
        } finally { latch.countDown(); service.stop(); }
    }
    private String url(String path) { return "http://127.0.0.1:"+port+path; }
    private HttpResponse<String> get(HttpClient client,String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> post(HttpClient client,String path,String body,String csrf,String origin,String type) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url(path))).header("Origin",origin).header("Content-Type",type);
        if(csrf!=null) builder.header("X-CSRF-TOKEN",csrf);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void thoughtsAreAccountScopedAndCanBeOverwrittenOrCleared() {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        String id="thought-fixture-"+UUID.randomUUID();
        ledger.saveThought(user,id,"初稿");
        assertEquals("初稿",ledger.thoughts(user).get(id));
        assertFalse(ledger.thoughts(UUID.randomUUID()).containsKey(id));
        ledger.saveThought(user,id,"修改");
        assertEquals("修改",ledger.thoughts(user).get(id));
        ledger.saveThought(user,id,"");
        assertTrue(ledger.thoughts(user).containsKey(id));
        assertEquals("",ledger.thoughts(user).get(id));
    }
    @Test void localDeletionIsPersistentIdempotentAndAccountScoped() {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        String key="delete-fixture-"+UUID.randomUUID();
        Note note=new Note(key,"book","highlight",List.of(key,key+"-thought"),"正文");
        ledger.dismiss(user,note);
        ledger.dismiss(user,note);
        assertTrue(ledger.dismissedSources(user).containsAll(note.sourceKeys()));
        assertFalse(ledger.dismissedSources(UUID.randomUUID()).contains(key));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM dismissed_source WHERE user_id=? AND source_key IN (?,?)",Integer.class,user,key,key+"-thought"));
        assertEquals("ready",ledger.status(user,note)); // Delivery history is untouched.
    }
    @Test void authenticationCsrfCookieSessionLogoutAndThrottling() throws Exception {
        db.update("DELETE FROM login_throttle");
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        assertEquals(401,get(client,"/api/status").statusCode());
        var response=get(client,"/api/auth/session"); var session=mapper.readTree(response.body());
        assertFalse(session.get("authenticated").asBoolean());
        String cookie=response.headers().firstValue("set-cookie").orElseThrow();
        assertTrue(cookie.toLowerCase().contains("httponly")); assertTrue(cookie.toLowerCase().contains("samesite=strict"));
        String token=session.get("csrfToken").asText(); String form="username=testowner&password=Test-only-Password-2026";
        assertEquals(403,post(client,"/api/auth/login",form,null,"http://127.0.0.1:18081","application/x-www-form-urlencoded").statusCode());
        assertEquals(403,post(client,"/api/auth/login",form,token,"https://evil.example","application/x-www-form-urlencoded").statusCode());
        assertEquals(200,post(client,"/api/auth/login",form,token,"http://127.0.0.1:18081","application/x-www-form-urlencoded").statusCode());
        session=mapper.readTree(get(client,"/api/auth/session").body()); assertTrue(session.get("authenticated").asBoolean());
        token=session.get("csrfToken").asText();
        assertEquals(200,get(client,"/api/status").statusCode());
        String secretBody="{\"wereadApiKey\":\"wrk-http-test\",\"flomoApiUrl\":\"https://flomoapp.com/iwh/123/test-secret/\"}";
        assertEquals(403,post(client,"/api/account/credentials",secretBody,null,"http://127.0.0.1:18081","application/json").statusCode());
        var saved=post(client,"/api/account/credentials",secretBody,token,"http://127.0.0.1:18081","application/json");
        assertEquals(200,saved.statusCode()); assertFalse(saved.body().contains("wrk-http-test")); assertFalse(saved.body().contains("test-secret"));
        var stored=mapper.readTree(get(client,"/api/account/credentials").body());
        assertTrue(stored.get("wereadConfigured").asBoolean()); assertEquals(3,stored.size());
        String revealPath="/api/account/credentials/reveal", selection="{\"provider\":\"weread\"}";
        assertEquals(403,post(client,revealPath,selection,null,"http://127.0.0.1:18081","application/json").statusCode());
        assertEquals(403,post(client,revealPath,selection,token,"https://evil.example","application/json").statusCode());
        var revealed=post(client,revealPath,selection,token,"http://127.0.0.1:18081","application/json");
        assertEquals(200,revealed.statusCode());
        assertEquals("wrk-http-test",mapper.readTree(revealed.body()).get("value").asText());
        assertTrue(revealed.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals("https://flomoapp.com/iwh/123/test-secret/",mapper.readTree(post(client,revealPath,"{\"provider\":\"flomo\"}",token,"http://127.0.0.1:18081","application/json").body()).get("value").asText());
        assertEquals(400,post(client,revealPath,"{\"provider\":\"unknown\"}",token,"http://127.0.0.1:18081","application/json").statusCode());
        assertEquals(400,post(client,revealPath,"{}",token,"http://127.0.0.1:18081","application/json").statusCode());
        assertEquals(200,post(client,"/api/account/credentials/delete","{\"provider\":\"weread\"}",token,"http://127.0.0.1:18081","application/json").statusCode());
        assertFalse(mapper.readTree(get(client,"/api/account/credentials").body()).get("wereadConfigured").asBoolean());
        assertEquals(400,post(client,revealPath,selection,token,"http://127.0.0.1:18081","application/json").statusCode());
        assertEquals(403,post(client,"/api/books","{}",null,"http://127.0.0.1:18081","application/json").statusCode());
        assertTrue(db.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name='testowner'",Integer.class)>0);
        String hash=db.queryForObject("SELECT password_hash FROM app_user WHERE username='testowner'",String.class);
        assertNotEquals("Test-only-Password-2026",hash); assertTrue(encoder.matches("Test-only-Password-2026",hash));
        assertEquals(204,post(client,"/api/auth/logout","{}",token,"http://127.0.0.1:18081","application/json").statusCode());
        assertEquals(401,get(client,"/api/status").statusCode());
        session=mapper.readTree(get(client,"/api/auth/session").body()); token=session.get("csrfToken").asText();
        assertEquals(401,post(client,revealPath,"{\"provider\":\"flomo\"}",token,"http://127.0.0.1:18081","application/json").statusCode());
        db.update("DELETE FROM login_throttle");
        for(int i=0;i<10;i++) assertEquals(401,post(client,"/api/auth/login","username=testowner&password=wrong",token,"http://127.0.0.1:18081","application/x-www-form-urlencoded").statusCode());
        assertEquals(429,post(client,"/api/auth/login",form,token,"http://127.0.0.1:18081","application/x-www-form-urlencoded").statusCode());
    }
    @Test void publicRegistrationIsCsrfProtectedLimitedAndCreatesIsolatedAccount() throws Exception {
        db.update("DELETE FROM login_throttle");
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String token = mapper.readTree(get(client,"/api/auth/session").body()).get("csrfToken").asText();
        String name = "reader-" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("username",name,"password","New-reader-password-2026"));
        String origin = "http://127.0.0.1:18081";
        assertEquals(403, post(client,"/api/auth/register",body,null,origin,"application/json").statusCode());
        assertEquals(403, post(client,"/api/auth/register",body,token,"https://evil.example","application/json").statusCode());
        assertEquals(400, post(client,"/api/auth/register","{}",token,origin,"application/json").statusCode());
        assertEquals(201, post(client,"/api/auth/register",body,token,origin,"application/json").statusCode());
        assertEquals(409, post(client,"/api/auth/register",body,token,origin,"application/json").statusCode());
        assertFalse(mapper.readTree(get(client,"/api/auth/session").body()).get("authenticated").asBoolean());
        String hash = db.queryForObject("SELECT password_hash FROM app_user WHERE username=?",String.class,name);
        assertTrue(encoder.matches("New-reader-password-2026",hash));
        assertEquals(200, post(client,"/api/auth/login","username="+name+"&password=New-reader-password-2026",token,origin,"application/x-www-form-urlencoded").statusCode());
        UUID owner = db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        credentials.save(owner, new Credentials.Change("wrk-owner-only", "https://flomoapp.com/iwh/123/owner-only/"));
        var status = mapper.readTree(get(client,"/api/status").body());
        assertFalse(status.get("wereadConfigured").asBoolean());
        assertFalse(status.get("flomoConfigured").asBoolean());
        assertEquals(0, mapper.readTree(get(client,"/api/sync").body()).get("sync").size());
        token = mapper.readTree(get(client,"/api/auth/session").body()).get("csrfToken").asText();
        assertEquals(400, post(client,"/api/account/credentials/reveal","{\"provider\":\"weread\"}",token,origin,"application/json").statusCode());
        assertEquals(400, post(client,"/api/auth/register",body,token,origin,"application/json").statusCode());
        assertEquals(204, post(client,"/api/auth/logout","{}",token,origin,"application/json").statusCode());
        token = mapper.readTree(get(client,"/api/auth/session").body()).get("csrfToken").asText();
        assertEquals(400, post(client,"/api/auth/register","{}",token,origin,"application/json").statusCode());
        assertEquals(429, post(client,"/api/auth/register",body,token,origin,"application/json").statusCode());
    }
    @Test void credentialsAreEncryptedScopedAndPartialUpdatesAreAtomic() {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        db.update("DELETE FROM user_credential WHERE user_id=?",user);
        credentials.save(user,new Credentials.Change("wrk-test-one",null));
        assertEquals(Map.of("wereadConfigured",true,"flomoConfigured",false,"sendingEnabled",false),credentials.status(user));
        assertEquals("wrk-test-one",credentials.required(user,"weread"));
        credentials.save(user,new Credentials.Change(null,"https://flomoapp.com/iwh/123/test-one/"));
        String stored=db.queryForObject("SELECT ciphertext FROM user_credential WHERE user_id=? AND provider='weread'",String.class,user);
        assertFalse(stored.contains("wrk-test-one")); assertTrue(stored.startsWith("v1."));
        assertEquals("wrk-test-one",credentials.required(user,"weread"));
        assertThrows(ApiException.class,() -> credentials.required(UUID.randomUUID(),"weread"));
        assertEquals("wrk-test-one",credentials.reveal(user,"weread").value());
        assertFalse(credentials.reveal(user,"weread").toString().contains("wrk-test-one"));
        assertThrows(ApiException.class,() -> credentials.reveal(UUID.randomUUID(),"weread"));
        credentials.save(user,new Credentials.Change(null,"https://flomoapp.com/iwh/123/test-two/"));
        assertEquals("wrk-test-one",credentials.required(user,"weread"));
        assertThrows(ApiException.class,() -> credentials.save(user,new Credentials.Change("wrk-new","http://127.0.0.1/")));
        assertEquals("wrk-test-one",credentials.required(user,"weread"));
        credentials.delete(user,"weread");
        assertThrows(ApiException.class,() -> credentials.required(user,"weread"));
        assertEquals("https://flomoapp.com/iwh/123/test-two/",credentials.required(user,"flomo"));
        credentials.delete(user,"flomo");
    }
    @Test void importIsRepeatableAndDoesNotOverwriteSentState() throws Exception {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        var fixture=mapper.readTree("{\"version\":1,\"deliveries\":[{\"noteId\":\"import-fixture\",\"content\":\"迁移测试\",\"status\":\"sent\",\"sourceKeys\":[\"import-fixture\"]}]}");
        importer.importData(fixture); importer.importData(fixture);
        assertEquals(1,db.queryForObject("SELECT count(*) FROM delivery WHERE user_id=? AND note_id='import-fixture'",Integer.class,user));
        assertEquals("sent",db.queryForObject("SELECT status FROM delivery WHERE user_id=? AND note_id='import-fixture'",String.class,user));
    }
    @Test void postgresPersistsAndScopesLedger() {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        Note note=new Note("fixture-note","book","highlight",List.of("source-1","source-2"),"测试正文");
        db.update("DELETE FROM delivery_source WHERE user_id=?",user); db.update("DELETE FROM delivery WHERE user_id=?",user);
        assertEquals("ready",ledger.status(user,note)); ledger.reserve(user,note); assertEquals("sending",ledger.status(user,note));
        ledger.finish(user,note.id(),"sent"); assertEquals("sent",ledger.status(user,note));
        assertEquals("ready",ledger.status(UUID.randomUUID(),note));
        assertThrows(ApiException.class,() -> ledger.reserve(user,note));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM delivery WHERE user_id=?",Integer.class,user));
        assertEquals(Map.of("source-1","sent","source-2","sent"),ledger.sourceStatuses(user));
        assertThrows(ApiException.class,() -> ledger.resolveUncertain(user,note,true));
    }
    @Test void uncertainCanBeConfirmedOrReleasedAfterManualCheck() {
        UUID user=db.queryForObject("SELECT id FROM app_user WHERE username='testowner'",UUID.class);
        db.update("DELETE FROM delivery_source WHERE user_id=?",user); db.update("DELETE FROM delivery WHERE user_id=?",user);
        Note a=new Note("uncertain-a","book","highlight",List.of("ua"),"甲"), b=new Note("uncertain-b","book","highlight",List.of("ub"),"乙");
        for(Note n:List.of(a,b)) { ledger.reserve(user,n); ledger.finish(user,n.id(),"uncertain"); }
        ledger.resolveUncertain(user,a,true); assertEquals("sent",ledger.status(user,a));
        ledger.resolveUncertain(user,b,false); assertEquals("ready",ledger.status(user,b));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM delivery_source WHERE user_id=? AND note_id='uncertain-b'",Integer.class,user));
        ledger.reserve(user,b); assertEquals("sending",ledger.status(user,b));
    }
}
