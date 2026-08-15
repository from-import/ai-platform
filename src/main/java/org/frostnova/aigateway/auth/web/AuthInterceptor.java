package org.frostnova.aigateway.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.service.AuthService;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Locale;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_PRINCIPAL_ATTRIBUTE =
            "org.frostnova.aigateway.auth.model.AuthPrincipal";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = bearerToken(request.getHeader("Authorization"));
        try {
            AuthPrincipal principal = authService.authenticate(token);
            request.setAttribute(AUTH_PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (BaseException exception) {
            if (ErrorCodes.AUTHENTICATION_REQUIRED.equals(exception.getCode())
                    && isBrowserNavigation(request)) {
                response.sendRedirect(request.getContextPath() + "/#/login");
                return false;
            }
            throw exception;
        }
    }

    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(
                        true,
                        0,
                        BEARER_PREFIX,
                        0,
                        BEARER_PREFIX.length()
                )) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    static boolean isBrowserNavigation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        if ("navigate".equalsIgnoreCase(fetchMode)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("text/html");
    }
}
