package com.nzxhjy.agri.portal.interceptor;

import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.common.security.AuthConstants;
import com.nzxhjy.agri.common.security.AuthResponseWriter;
import com.nzxhjy.agri.common.security.JwtUtils;
import com.nzxhjy.agri.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PortalAuthInterceptor implements HandlerInterceptor {
    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            AuthResponseWriter.unauthorized(response);
            return false;
        }
        try {
            Long userId = jwtUtils.parseUserId(token);
            if (!AuthConstants.PORTAL_CLIENT.equals(jwtUtils.parseClientType(token))
                    || !token.equals(redisUtils.get(AuthConstants.tokenKey(userId)))) {
                AuthResponseWriter.unauthorized(response);
                return false;
            }
            UserContext.set(userId, AuthConstants.PORTAL_CLIENT);
            return true;
        } catch (RuntimeException exception) {
            AuthResponseWriter.unauthorized(response);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
    }
}
