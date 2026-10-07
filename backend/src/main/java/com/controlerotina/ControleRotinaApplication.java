package com.controlerotina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ControleRotinaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControleRotinaApplication.class, args);
    }
}
