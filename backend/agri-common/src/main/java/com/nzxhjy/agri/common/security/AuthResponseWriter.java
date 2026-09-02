package com.nzxhjy.agri.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.model.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class AuthResponseWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AuthResponseWriter() {
    }

    public static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(),
                Result.failure(ErrorCodeEnum.UNAUTHORIZED.getCode(), ErrorCodeEnum.UNAUTHORIZED.getMessage()));
    }
}
