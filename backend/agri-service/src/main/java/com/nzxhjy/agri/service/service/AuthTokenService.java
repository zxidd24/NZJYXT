package com.nzxhjy.agri.service.service;

import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.common.security.AuthConstants;
import com.nzxhjy.agri.common.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;

    public String issue(Long userId, String clientType) {
        String token = jwtUtils.generateToken(userId, clientType);
        redisUtils.set(AuthConstants.tokenKey(userId), token, Duration.ofMillis(jwtUtils.getExpiration()));
        return token;
    }

    public Long validate(String token, String expectedClientType) {
        Long userId = jwtUtils.parseUserId(token);
        String clientType = jwtUtils.parseClientType(token);
        String activeToken = redisUtils.get(AuthConstants.tokenKey(userId));
        if (!expectedClientType.equals(clientType) || !token.equals(activeToken)) {
            throw new IllegalArgumentException("token is inactive");
        }
        return userId;
    }

    public void revoke(Long userId) {
        redisUtils.delete(AuthConstants.tokenKey(userId));
    }
}
