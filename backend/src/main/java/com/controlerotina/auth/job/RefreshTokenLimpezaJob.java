package com.controlerotina.auth.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.controlerotina.auth.service.RefreshTokenService;

@Component
public class RefreshTokenLimpezaJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenLimpezaJob.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenLimpezaJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public void limparTokensAntigos() {
        int removidos = refreshTokenService.limparTokensAntigos();
        if (removidos > 0) {
            log.info("Removidos {} refresh tokens expirados/revogados antigos", removidos);
        }
    }
}
