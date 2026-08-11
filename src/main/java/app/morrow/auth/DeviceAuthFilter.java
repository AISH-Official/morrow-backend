package app.morrow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class DeviceAuthFilter extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = "morrow.auth.userId";
    private final DeviceAuthService service;
    private final boolean required;

    public DeviceAuthFilter(DeviceAuthService service, @Value("${morrow.auth.required:false}") boolean required) {
        this.service = service;
        this.required = required;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        var publicAuth = path.equals("/api/v1/auth/device") || path.equals("/api/v1/auth/pair")
                || path.equals("/api/v1/auth/account") || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/logout") || path.equals("/api/v1/auth/pairing-code");
        return publicAuth || path.startsWith("/actuator/") || "OPTIONS".equals(request.getMethod());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        var token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        var userId = service.authenticate(token);
        if (token != null && userId == null) { unauthorized(response, "유효하지 않은 기기 토큰입니다."); return; }
        if (required && userId == null) { unauthorized(response, "기기 인증이 필요합니다."); return; }
        if (userId != null) request.setAttribute(USER_ATTRIBUTE, userId);
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
