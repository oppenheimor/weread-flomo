package cn.sparkden.weread;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AiBrowserTest {
    @Test void browserGatewayOnlyAcceptsExplicitLoopbackApiRoots() {
        assertEquals("http://localhost:3425/v1",AiProviderService.browserUrl("http://localhost:3425/v1/"));
        assertEquals("http://127.0.0.1:3425/v1",AiProviderService.browserUrl("http://127.0.0.1:3425/v1"));
        for(String url:new String[]{"https://example.com/v1","http://localhost.evil.com/v1","http://user:pass@localhost/v1","http://localhost/v1?key=secret","http://localhost/v1#fragment","http://localhost/admin","file:///v1","http://192.168.1.20:3425/v1"}) {
            assertThrows(ApiException.class,() -> AiProviderService.browserUrl(url),url);
        }
        // Browser support must not loosen the server-side SSRF policy.
        assertThrows(ApiException.class,() -> AiTransport.validUrl("http://127.0.0.1:3425/v1"));
    }
}
