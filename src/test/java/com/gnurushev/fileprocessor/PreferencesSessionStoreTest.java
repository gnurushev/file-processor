package com.gnurushev.fileprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class PreferencesSessionStoreTest {
    @Test
    void savesLoadsAndClearsSessionData() throws Exception {
        Preferences preferences = Preferences.userRoot().node("/com/gnurushev/fileprocessor/test/" + UUID.randomUUID());
        PreferencesSessionStore store = new PreferencesSessionStore(preferences);
        UserSession session = new UserSession("demo.user", "token-123", Instant.parse("2026-08-12T18:00:00Z"));

        store.save(session);

        UserSession loadedSession = store.load().orElseThrow();
        assertEquals(session, loadedSession);

        store.clear();
        assertTrue(store.load().isEmpty());
        preferences.removeNode();
    }
}
