package com.calcforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CalcForge - a local-first, all-in-one calculator.
 *
 * <p>Every endpoint under {@code /api/v1/local/**} is fully deterministic and requires
 * no network connection beyond reaching this server, and no authentication. Everything
 * under {@code /api/v1/cloud/**} is strictly additive (accounts, sync, AI assistance,
 * live rates) and the application is 100% usable without ever calling it.</p>
 */
@SpringBootApplication
@EnableScheduling
public class CalcForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalcForgeApplication.class, args);
    }
}
