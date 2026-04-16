package com.mcqbuddy.attempt.api.dto;

import java.time.Instant;

public record StartAttemptResponse(
        int attemptId,
        int examPaperId,
        Instant startedAt,
        Integer totalTimeSeconds
) {
}
