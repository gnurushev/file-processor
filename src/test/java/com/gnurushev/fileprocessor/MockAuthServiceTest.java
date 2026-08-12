package com.gnurushev.fileprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class MockAuthServiceTest {
    @Test
    void returnsSessionWhenCredentialsAreValid() {
        MockAuthService service = new MockAuthService(new AppConfig("Test", 44567, 96, Duration.ZERO));

        UserSession session = service.login(new LoginCredentials("demo.user", "secret"))
            .toCompletableFuture()
            .join();

        assertEquals("demo.user", session.username());
        assertTrue(session.accessToken().startsWith("mock-token-"));
    }

    @Test
    void rejectsShortPasswords() {
        MockAuthService service = new MockAuthService(new AppConfig("Test", 44567, 96, Duration.ZERO));

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> service.login(new LoginCredentials("demo.user", "abc")).toCompletableFuture().join()
        );

        assertEquals("Enter a password with at least 4 characters.", error.getCause().getMessage());
    }
}

