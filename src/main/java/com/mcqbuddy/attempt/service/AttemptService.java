package com.mcqbuddy.attempt.service;

import com.mcqbuddy.attempt.repository.AttemptRepository;
import com.mcqbuddy.bean.entity.attempt.Attempt;
import com.mcqbuddy.bean.entity.attempt.AttemptAnswer;
import com.mcqbuddy.bean.entity.attempt.AttemptQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AttemptService {

    private static final Logger logger = LoggerFactory.getLogger(AttemptService.class);
    private final AttemptRepository attemptRepository;

    public AttemptService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public Attempt create(Attempt attempt) {
        try {
            normalizeGraph(attempt);
            return attemptRepository.save(attempt);
        } catch (Exception e) {
            logger.error("Attempt create", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Attempt create failed", e);
        }
    }

    public List<Attempt> list() {
        return attemptRepository.findAll();
    }

    public Attempt get(int id) {
        try {
            return attemptRepository.findDetailById(id)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attempt not found"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Attempt get", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Attempt get failed", e);
        }
    }

    public Attempt update(int id, Attempt incoming) {
        Optional<Attempt> existingOpt = attemptRepository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Attempt not found");
        }
        Attempt existing = existingOpt.get();
        if (incoming.getExamPublicKey() != null) {
            existing.setExamPublicKey(incoming.getExamPublicKey());
        }
        if (incoming.getStartedAt() != null) {
            existing.setStartedAt(incoming.getStartedAt());
        }
        if (incoming.getTotalTimeSeconds() != null) {
            existing.setTotalTimeSeconds(incoming.getTotalTimeSeconds());
        }
        if (incoming.getAttemptQuestions() != null && !incoming.getAttemptQuestions().isEmpty()) {
            existing.getAttemptQuestions().clear();
            for (AttemptQuestion q : incoming.getAttemptQuestions()) {
                existing.addQuestion(q);
                if (q.getAttemptAnswers() != null) {
                    for (AttemptAnswer a : q.getAttemptAnswers()) {
                        q.addAnswer(a);
                    }
                }
            }
        }
        try {
            normalizeGraph(existing);
            return attemptRepository.save(existing);
        } catch (Exception e) {
            logger.error("Attempt update", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Attempt update failed", e);
        }
    }

    public void delete(int id) {
        Optional<Attempt> existingOpt = attemptRepository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Attempt not found");
        }
        attemptRepository.delete(existingOpt.get());
    }

    private static void normalizeGraph(Attempt attempt) {
        if (attempt == null || attempt.getAttemptQuestions() == null) {
            return;
        }
        for (AttemptQuestion q : attempt.getAttemptQuestions()) {
            q.setAttempt(attempt);
            if (q.getAttemptAnswers() == null) {
                continue;
            }
            for (AttemptAnswer a : q.getAttemptAnswers()) {
                a.setAttemptQuestion(q);
            }
        }
    }
}
