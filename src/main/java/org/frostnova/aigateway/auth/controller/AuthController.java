package org.frostnova.aigateway.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.frostnova.aigateway.auth.api.LoginRequest;
import org.frostnova.aigateway.auth.api.LoginResponse;
import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.service.AuthService;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.login(request));
    }

    @GetMapping("/me")
    public UserView me(
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return UserView.from(principal);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.bearerToken(request.getHeader("Authorization")));
        return ResponseEntity.noContent().build();
    }
}
