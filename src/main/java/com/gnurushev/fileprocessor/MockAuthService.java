package com.gnurushev.fileprocessor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MockAuthService implements AuthService {
    private final AppConfig config;

    public MockAuthService(AppConfig config) {
        this.config = config;
    }

    @Override
    public CompletionStage<UserSession> login(LoginCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            pause();

            String username = credentials.username() == null ? "" : credentials.username().strip();
            String password = credentials.password() == null ? "" : credentials.password();

            if (username.isEmpty()) {
                throw new IllegalArgumentException("Enter a username.");
            }
            if (password.length() < 4) {
                throw new IllegalArgumentException("Enter a password with at least 4 characters.");
            }

            return new UserSession(username, "mock-token-" + UUID.randomUUID(), Instant.now());
        });
    }

    private void pause() {
        try {
            Thread.sleep(config.mockLatency().toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Login was interrupted.", error);
        }
    }
}

