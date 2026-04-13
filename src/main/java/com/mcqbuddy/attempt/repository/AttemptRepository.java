package com.mcqbuddy.attempt.repository;

import com.mcqbuddy.bean.entity.attempt.Attempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Integer> {

    @EntityGraph(attributePaths = {"attemptQuestions", "attemptQuestions.attemptAnswers"})
    Optional<Attempt> findDetailById(Integer id);

    @EntityGraph(attributePaths = {"attemptQuestions", "attemptQuestions.attemptAnswers"})
    Optional<Attempt> findFirstByExamPaperIdOrderByIdDesc(Integer examPaperId);
}
