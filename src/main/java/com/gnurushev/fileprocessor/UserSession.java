package com.gnurushev.fileprocessor;

import java.time.Instant;

public record UserSession(String username, String accessToken, Instant authenticatedAt) {
}

