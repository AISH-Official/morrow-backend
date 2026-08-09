package app.morrow.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestUserResolver {
    private final HttpServletRequest request;
    public RequestUserResolver(HttpServletRequest request) { this.request = request; }

    public String resolve(String requestedUserId) {
        var authenticated = request.getAttribute(DeviceAuthFilter.USER_ATTRIBUTE);
        var requested = requestedUserId == null || requestedUserId.isBlank() ? "default-user" : requestedUserId;
        if (authenticated == null) return requested;
        if (!authenticated.equals(requested) && !"default-user".equals(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 데이터에 접근할 수 없습니다.");
        }
        return authenticated.toString();
    }
}
