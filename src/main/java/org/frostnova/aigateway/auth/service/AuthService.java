package org.frostnova.aigateway.auth.service;

import org.frostnova.aigateway.auth.api.LoginRequest;
import org.frostnova.aigateway.auth.api.LoginResponse;
import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.config.AuthProperties;
import org.frostnova.aigateway.auth.mapper.AuthMapper;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.AuthSession;
import org.frostnova.aigateway.auth.model.UserAccount;
import org.frostnova.aigateway.auth.model.UserRole;
import org.frostnova.aigateway.auth.model.UserStatus;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final int TOKEN_BYTES = 32;

    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthMapper mapper,
            PasswordEncoder passwordEncoder,
            AuthProperties properties
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        if (!properties.isRegistrationEnabled()) {
            throw new BaseException(
                    ErrorCodes.REGISTRATION_DISABLED,
                    "Public registration is disabled",
                    HttpStatus.FORBIDDEN
            );
        }
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        UserAccount user = new UserAccount();
        user.setUsername(normalizeUsername(request.username()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(normalizeDisplayName(request.displayName(), user.getUsername()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        try {
            mapper.insertUser(user);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(
                    ErrorCodes.USERNAME_ALREADY_EXISTS,
                    "Username is already registered",
                    HttpStatus.CONFLICT,
                    exception
            );
        }
        return UserView.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = mapper.findUserByUsername(normalizeUsername(request.username()));
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BaseException(
                    ErrorCodes.INVALID_CREDENTIALS,
                    "Username or password is incorrect",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return issueSession(user);
    }

    private LoginResponse issueSession(UserAccount user) {
        LocalDateTime now = utcNow();
        LocalDateTime expiresAt = now.plus(requireValidSessionTtl());
        String token = generateToken();
        AuthSession session = AuthSession.builder()
                .userId(user.getId())
                .tokenHash(hashToken(token))
                .expiresAt(expiresAt)
                .createdAt(now)
                .lastUsedAt(now)
                .build();
        mapper.insertSession(session);

        return new LoginResponse(
                token,
                "Bearer",
                expiresAt.atOffset(ZoneOffset.UTC),
                UserView.from(user)
        );
    }

    public AuthPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw authenticationRequired();
        }
        LocalDateTime now = utcNow();
        AuthPrincipal principal = mapper.findActivePrincipal(hashToken(token), now);
        if (principal == null) {
            throw authenticationRequired();
        }
        mapper.touchSession(principal.getSessionId(), now);
        return principal;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            mapper.revokeSession(hashToken(token), utcNow());
        }
    }

    private BaseException authenticationRequired() {
        return new BaseException(
                ErrorCodes.AUTHENTICATION_REQUIRED,
                "A valid login session is required",
                HttpStatus.UNAUTHORIZED
        );
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private Duration requireValidSessionTtl() {
        Duration sessionTtl = properties.getSessionTtl();
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new BaseException(
                    ErrorCodes.GATEWAY_CONFIGURATION_ERROR,
                    "auth.session-ttl must be positive",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return sessionTtl;
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        return displayName == null || displayName.isBlank() ? fallback : displayName.trim();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
