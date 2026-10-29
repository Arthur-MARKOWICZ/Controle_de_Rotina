package com.controlerotina.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Jwt jwt,
        RefreshCookie refreshCookie
) {
    public record Jwt(
            String secret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            String issuer,
            String audience
    ) {
    }

    public record RefreshCookie(
            String name,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite
    ) {
    }
}
