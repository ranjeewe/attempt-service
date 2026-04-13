package com.mcqbuddy.attempt.api.dto;

public record ImportMarkingSchemeResponse(
        int markingSchemeId,
        int examPaperId,
        int importedQuestions,
        int importedCorrectAnswers
) {
}
