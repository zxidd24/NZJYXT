package com.nzxhjy.agri.admin;

import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.common.security.AuthConstants;
import com.nzxhjy.agri.common.security.JwtUtils;
import com.nzxhjy.agri.common.security.MaskUtils;
import com.nzxhjy.agri.common.security.PasswordUtils;
import com.nzxhjy.agri.service.service.AuthTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityUtilitiesTest {
    @Test
    void seededAdminPasswordMatchesManualPassword() {
        PasswordUtils passwordUtils = new PasswordUtils();
        assertTrue(passwordUtils.matches("123456",
                "$2b$10$9TEuQ4cWQTjJOAGN0OEJnOKZbv3wJePvPtPSXkoDh2BFqDgvTtaBC"));
    }

    @Test
    void tokenCannotCrossAdminAndPortalClients() {
        JwtUtils jwtUtils = new JwtUtils("0123456789abcdef0123456789abcdef", 7_200_000);
        RedisUtils redisUtils = mock(RedisUtils.class);
        AuthTokenService tokenService = new AuthTokenService(jwtUtils, redisUtils);

        String token = tokenService.issue(9L, AuthConstants.ADMIN_CLIENT);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisUtils).set(eq(AuthConstants.tokenKey(9L)), tokenCaptor.capture(), eq(Duration.ofMillis(7_200_000)));
        when(redisUtils.get(AuthConstants.tokenKey(9L))).thenReturn(token);

        assertEquals(9L, tokenService.validate(token, AuthConstants.ADMIN_CLIENT));
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.validate(token, AuthConstants.PORTAL_CLIENT));
    }

    @Test
    void revokedTokenIsRejectedImmediately() {
        JwtUtils jwtUtils = new JwtUtils("0123456789abcdef0123456789abcdef", 7_200_000);
        RedisUtils redisUtils = mock(RedisUtils.class);
        AuthTokenService tokenService = new AuthTokenService(jwtUtils, redisUtils);

        String token = tokenService.issue(9L, AuthConstants.ADMIN_CLIENT);
        when(redisUtils.get(AuthConstants.tokenKey(9L))).thenReturn(token, (String) null);

        assertEquals(9L, tokenService.validate(token, AuthConstants.ADMIN_CLIENT));
        tokenService.revoke(9L);
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.validate(token, AuthConstants.ADMIN_CLIENT));
        verify(redisUtils).delete(AuthConstants.tokenKey(9L));
    }

    @Test
    void sensitiveValuesRoundTripAndRemainMasked() {
        AesUtils aesUtils = new AesUtils("test-aes-key");
        String idCard = "110101199001011234";
        String encrypted = aesUtils.encrypt(idCard);
        assertEquals(idCard, aesUtils.decrypt(encrypted));
        assertEquals("110101********1234", MaskUtils.idCard(idCard));
        assertEquals("6222************8888", MaskUtils.bankCard("62220000111122228888"));
    }
}
