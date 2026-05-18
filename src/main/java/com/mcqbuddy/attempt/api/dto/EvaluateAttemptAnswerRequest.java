package com.mcqbuddy.attempt.api.dto;

public record EvaluateAttemptAnswerRequest(
        Integer attemptId,
        String examPublicKey,
        Integer questionNumber,
        Integer selectedOptionNumber,
        String selectedAnswerText
) {
}
