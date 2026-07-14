package br.com.topsdojob.v3.application.publico.auth;

import jakarta.servlet.http.HttpSession;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PublicSessionRegistry {
    private final ConcurrentHashMap<UUID, Set<HttpSession>> sessions = new ConcurrentHashMap<>();

    public void register(UUID usuarioId, HttpSession session) {
        sessions.computeIfAbsent(usuarioId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void invalidateAll(UUID usuarioId) {
        Set<HttpSession> userSessions = sessions.remove(usuarioId);
        if (userSessions == null) return;
        userSessions.forEach(session -> {
            try { session.invalidate(); } catch (IllegalStateException ignored) { }
        });
    }
}
