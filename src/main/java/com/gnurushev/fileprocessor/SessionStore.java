package com.gnurushev.fileprocessor;

import java.util.Optional;

public interface SessionStore {
    Optional<UserSession> load();

    void save(UserSession session);

    void clear();
}

