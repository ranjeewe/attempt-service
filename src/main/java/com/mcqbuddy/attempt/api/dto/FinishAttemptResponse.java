package com.mcqbuddy.attempt.api.dto;

public record FinishAttemptResponse(
        int attemptId,
        int examPaperId,
        int correctAnswers,
        int totalQuestions,
        double percentage
) {
}
