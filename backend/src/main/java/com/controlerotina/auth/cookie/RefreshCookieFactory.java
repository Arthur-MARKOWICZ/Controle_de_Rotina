package com.controlerotina.auth.cookie;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.controlerotina.config.SecurityProperties;

@Component
public class RefreshCookieFactory {

    private final SecurityProperties securityProperties;

    public RefreshCookieFactory(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public ResponseCookie criar(String refreshToken) {
        var cookie = securityProperties.refreshCookie();
        return base(cookie.name(), refreshToken)
                .maxAge(securityProperties.jwt().refreshTokenTtl())
                .build();
    }

    public ResponseCookie limpar() {
        var cookie = securityProperties.refreshCookie();
        return base(cookie.name(), "")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        var cookie = securityProperties.refreshCookie();
        return ResponseCookie.from(name, value)
                .httpOnly(cookie.httpOnly())
                .secure(cookie.secure())
                .path(cookie.path())
                .sameSite(cookie.sameSite());
    }
}
