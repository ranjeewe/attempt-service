package com.mcqbuddy.attempt.repository;

import com.mcqbuddy.bean.entity.markingscheme.MarkingScheme;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarkingSchemeRepository extends JpaRepository<MarkingScheme, Integer> {

    @EntityGraph(attributePaths = {"questions", "questions.correctAnswers"})
    Optional<MarkingScheme> findDetailById(Integer id);

    @EntityGraph(attributePaths = {"questions", "questions.correctAnswers"})
    Optional<MarkingScheme> findDetailByExamPublicKeyIgnoreCase(String examPublicKey);
}
