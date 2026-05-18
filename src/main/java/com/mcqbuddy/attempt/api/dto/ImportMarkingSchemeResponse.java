package com.mcqbuddy.attempt.api.dto;

public record ImportMarkingSchemeResponse(
        int markingSchemeId,
        String examPublicKey,
        int importedQuestions,
        int importedCorrectAnswers
) {
}
