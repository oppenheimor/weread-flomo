package cn.sparkden.weread;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    static void json(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write(body);
    }
    @Bean SecurityFilterChain security(HttpSecurity http, Accounts accounts,
            @Value("${app.public-origin}") String origin,
            @Value("${server.servlet.session.cookie.secure}") boolean secure) throws Exception {
        URI uri = URI.create(origin);
        boolean local = ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty()
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local))
                || ("https".equals(uri.getScheme()) && !secure))
            throw new IllegalStateException("APP_PUBLIC_ORIGIN 必须是 HTTPS origin（并设置 COOKIE_SECURE=true），或仅用于 SSH 隧道的本机 HTTP origin");
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/share/**", "/api/public/shares/*").permitAll()
            .requestMatchers("/", "/introduction", "/introduction/", "/index.html", "/theme-init.js", "/assets/**", "/error", "/health", "/api/auth/session", "/api/auth/login", "/api/auth/register").permitAll()
            .anyRequest().authenticated());
        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
            .successHandler((req,res,auth) -> json(res,200,"{\"ok\":true}"))
            .failureHandler((req,res,error) -> json(res,401,"{\"error\":\"账号或密码错误\"}")));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
            .invalidateHttpSession(true).deleteCookies("WEREAD_SESSION")
            .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)));
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((req,res,error) -> json(res,401,"{\"error\":\"请先登录\"}"))
            .accessDeniedHandler((req,res,error) -> json(res,403,"{\"error\":\"请求校验失败，请刷新页面后重试\"}")));
        http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https://cdn.weread.qq.com https://wfqqreader-1252317822.image.myqcloud.com; connect-src 'self' http://localhost:* http://127.0.0.1:* https://localhost:* https://127.0.0.1:*; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String supplied = req.getHeader("Origin");
                boolean writing = !"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod());
                if ((writing && !origin.equals(supplied)) || (supplied != null && !origin.equals(supplied))) {
                    json(res,403,"{\"error\":\"拒绝跨站请求\"}"); return;
                }
                chain.doFilter(req,res);
            }
        }, CsrfFilter.class);
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                if (req.getServletPath().equals("/api/auth/login") && req.getMethod().equals("POST") && !accounts.allowLogin(req.getParameter("username"), req.getRemoteAddr())) {
                    res.setHeader("Retry-After","300"); json(res,429,"{\"error\":\"登录尝试过多，请五分钟后重试\"}"); return;
                }
                if (req.getServletPath().equals("/api/auth/register") && req.getMethod().equals("POST") && !accounts.allowRegistration(req.getRemoteAddr())) {
                    res.setHeader("Retry-After","300"); json(res,429,"{\"error\":\"注册尝试过多，请五分钟后重试\"}"); return;
                }
                chain.doFilter(req,res);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
