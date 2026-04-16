package com.mcqbuddy.attempt.api.dto;

public record EvaluateAttemptAnswerRequest(
        Integer attemptId,
        Integer examPaperId,
        Integer questionNumber,
        Integer selectedOptionNumber,
        String selectedAnswerText
) {
}
