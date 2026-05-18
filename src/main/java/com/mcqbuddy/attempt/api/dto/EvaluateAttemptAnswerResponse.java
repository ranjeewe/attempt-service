package com.mcqbuddy.attempt.api.dto;

public record EvaluateAttemptAnswerResponse(
        int attemptId,
        String examPublicKey,
        int questionNumber,
        int selectedOptionNumber,
        int correctOptionNumber,
        boolean correct
) {
}
