package com.gnurushev.fileprocessor;

import java.util.concurrent.CompletionStage;

public interface AuthService {
    CompletionStage<UserSession> login(LoginCredentials credentials);
}

