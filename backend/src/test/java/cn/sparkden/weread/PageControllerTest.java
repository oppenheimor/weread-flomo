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
