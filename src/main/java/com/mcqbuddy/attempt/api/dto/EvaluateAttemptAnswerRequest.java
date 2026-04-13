package com.mcqbuddy.attempt.api.dto;

public record EvaluateAttemptAnswerRequest(
        Integer examPaperId,
        Integer questionNumber,
        Integer selectedOptionNumber,
        String selectedAnswerText
) {
}
