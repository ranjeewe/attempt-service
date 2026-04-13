package com.mcqbuddy.attempt.api.dto;

public record EvaluateAttemptAnswerResponse(
        int attemptId,
        int examPaperId,
        int questionNumber,
        int selectedOptionNumber,
        int correctOptionNumber,
        boolean correct
) {
}
