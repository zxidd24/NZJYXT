package com.nzxhjy.agri.common.security;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtils {
    public String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != null && encodedPassword != null && BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
