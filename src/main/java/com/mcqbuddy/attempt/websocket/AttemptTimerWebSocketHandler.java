package com.mcqbuddy.attempt.websocket;

import com.mcqbuddy.attempt.repository.AttemptRepository;
import com.mcqbuddy.bean.entity.attempt.Attempt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class AttemptTimerWebSocketHandler extends TextWebSocketHandler {

    private final AttemptRepository attemptRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public AttemptTimerWebSocketHandler(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer attemptId = parseAttemptId(session.getUri());
        if (attemptId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        Instant startedAt = attempt.getStartedAt();
        Integer totalSeconds = attempt.getTotalTimeSeconds();
        if (startedAt == null || totalSeconds == null || totalSeconds <= 0) {
            // No timer configured; send a single message and close.
            session.sendMessage(new TextMessage("{\"attemptId\":" + attemptId + ",\"remainingSeconds\":null}"));
            session.close(CloseStatus.NORMAL);
            return;
        }

        Runnable tick = () -> {
            try {
                if (!session.isOpen()) {
                    cancelJob(session.getId());
                    return;
                }
                long elapsed = Duration.between(startedAt, Instant.now()).getSeconds();
                long remaining = Math.max(0, (long) totalSeconds - elapsed);
                session.sendMessage(new TextMessage(
                        "{\"attemptId\":" + attemptId + ",\"remainingSeconds\":" + remaining + "}"
                ));
                if (remaining <= 0) {
                    session.close(CloseStatus.NORMAL);
                    cancelJob(session.getId());
                }
            } catch (IOException ignored) {
                cancelJob(session.getId());
            } catch (Exception ignored) {
                cancelJob(session.getId());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored2) {
                    // ignore
                }
            }
        };

        // Send immediately, then every second.
        tick.run();
        ScheduledFuture<?> job = scheduler.scheduleAtFixedRate(tick, 1, 1, TimeUnit.SECONDS);
        jobs.put(session.getId(), job);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelJob(session.getId());
    }

    private void cancelJob(String sessionId) {
        ScheduledFuture<?> job = jobs.remove(sessionId);
        if (job != null) {
            job.cancel(true);
        }
    }

    private static Integer parseAttemptId(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String query = uri.getQuery();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            String val = part.substring(idx + 1);
            if (!"attemptId".equals(key)) continue;
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

