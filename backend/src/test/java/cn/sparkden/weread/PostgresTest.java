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
