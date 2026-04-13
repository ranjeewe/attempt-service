package com.mcqbuddy.attempt.service;

import com.mcqbuddy.attempt.repository.MarkingSchemeRepository;
import com.mcqbuddy.bean.entity.markingscheme.CorrectAnswer;
import com.mcqbuddy.bean.entity.markingscheme.MarkingScheme;
import com.mcqbuddy.bean.entity.markingscheme.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarkingSchemeService {

    private static final Logger logger = LoggerFactory.getLogger(MarkingSchemeService.class);
    private final MarkingSchemeRepository repository;

    public MarkingSchemeService(MarkingSchemeRepository repository) {
        this.repository = repository;
    }

    public MarkingScheme create(MarkingScheme ms) {
        try {
            normalizeGraph(ms);
            return repository.save(ms);
        } catch (Exception e) {
            logger.error("MarkingScheme create", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "MarkingScheme create failed", e);
        }
    }

    public List<MarkingScheme> list() {
        return repository.findAll();
    }

    public MarkingScheme get(int id) {
        try {
            return repository.findDetailById(id)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "MarkingScheme not found"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("MarkingScheme get", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "MarkingScheme get failed", e);
        }
    }

    public MarkingScheme update(int id, MarkingScheme incoming) {
        Optional<MarkingScheme> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "MarkingScheme not found");
        }
        MarkingScheme existing = existingOpt.get();
        if (incoming.getQuestions() != null && !incoming.getQuestions().isEmpty()) {
            existing.getQuestions().clear();
            for (Question q : incoming.getQuestions()) {
                existing.addQuestion(q);
                if (q.getCorrectAnswers() != null) {
                    for (CorrectAnswer ca : q.getCorrectAnswers()) {
                        q.addCorrectAnswer(ca);
                    }
                }
            }
        }
        try {
            normalizeGraph(existing);
            return repository.save(existing);
        } catch (Exception e) {
            logger.error("MarkingScheme update", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "MarkingScheme update failed", e);
        }
    }

    public void delete(int id) {
        Optional<MarkingScheme> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "MarkingScheme not found");
        }
        repository.delete(existingOpt.get());
    }

    private static void normalizeGraph(MarkingScheme ms) {
        if (ms == null || ms.getQuestions() == null) {
            return;
        }
        for (Question q : ms.getQuestions()) {
            q.setMarkingScheme(ms);
            if (q.getCorrectAnswers() == null) {
                continue;
            }
            for (CorrectAnswer ca : q.getCorrectAnswers()) {
                ca.setQuestion(q);
            }
        }
    }
}
