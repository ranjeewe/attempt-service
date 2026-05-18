package com.mcqbuddy.attempt.api.dto;

import java.time.Instant;

public record StartAttemptResponse(
        int attemptId,
        String examPublicKey,
        Instant startedAt,
        Integer totalTimeSeconds
) {
}
