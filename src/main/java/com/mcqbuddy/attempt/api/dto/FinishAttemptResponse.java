package com.mcqbuddy.attempt.api.dto;

public record FinishAttemptResponse(
        int attemptId,
        String examPublicKey,
        int correctAnswers,
        int totalQuestions,
        double percentage
) {
}
