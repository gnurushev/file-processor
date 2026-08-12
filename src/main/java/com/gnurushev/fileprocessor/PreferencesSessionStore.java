package com.gnurushev.fileprocessor;

import java.time.Instant;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PreferencesSessionStore implements SessionStore {
    static final String NODE_PATH = "/com/gnurushev/fileprocessor";
    private static final String USERNAME_KEY = "username";
    private static final String TOKEN_KEY = "token";
    private static final String AUTHENTICATED_AT_KEY = "authenticatedAt";

    private final Preferences preferences;

    public PreferencesSessionStore() {
        this(Preferences.userRoot().node(NODE_PATH));
    }

    public PreferencesSessionStore(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public Optional<UserSession> load() {
        String username = preferences.get(USERNAME_KEY, "").strip();
        String token = preferences.get(TOKEN_KEY, "").strip();
        long authenticatedAt = preferences.getLong(AUTHENTICATED_AT_KEY, -1L);

        if (username.isEmpty() || token.isEmpty() || authenticatedAt < 0) {
            return Optional.empty();
        }

        return Optional.of(new UserSession(username, token, Instant.ofEpochMilli(authenticatedAt)));
    }

    @Override
    public void save(UserSession session) {
        preferences.put(USERNAME_KEY, session.username());
        preferences.put(TOKEN_KEY, session.accessToken());
        preferences.putLong(AUTHENTICATED_AT_KEY, session.authenticatedAt().toEpochMilli());
    }

    @Override
    public void clear() {
        try {
            preferences.clear();
        } catch (BackingStoreException error) {
            throw new IllegalStateException("Unable to clear the stored session.", error);
        }
    }
}

