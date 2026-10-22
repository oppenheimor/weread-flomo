package cn.sparkden.weread;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PageController.class, properties = {
    "app.public-origin=http://127.0.0.1:5175",
    "server.servlet.session.cookie.secure=false"
})
@Import(SecurityConfig.class)
class PageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean Accounts accounts;
    @MockitoBean ShareService shares;

    @Test void publicSharePagesHaveNoStoreAndInvalidLinksReturn404() throws Exception {
        String token="a".repeat(43);
        org.mockito.Mockito.when(shares.exists(token)).thenReturn(true);
        mvc.perform(get("/share/"+token)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Referrer-Policy","no-referrer"));
        for(String path : new String[]{"/share/missing", "/share", "/share/", "/share/bad/path"})
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/shimo/share/"+token).contextPath("/shimo").servletPath("/share/"+token))
            .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test void articleAndTrailingSlashArePublic() throws Exception {
        for (String path : new String[]{"/introduction", "/introduction/"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test void articleIsPublicUnderDeploymentContextPath() throws Exception {
        mvc.perform(get("/shimo/introduction").contextPath("/shimo").servletPath("/introduction"))
            .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test void privateApiAndUnknownPathsStillRequireLogin() throws Exception {
        for (String path : new String[]{"/api/books", "/api/status", "/api/account/credentials", "/introduction/private"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
