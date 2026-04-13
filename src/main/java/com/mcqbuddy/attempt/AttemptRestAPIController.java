package com.mcqbuddy.attempt;

import com.mcqbuddy.attempt.api.dto.EvaluateAttemptAnswerRequest;
import com.mcqbuddy.attempt.service.AttemptExamOrchestrationService;
import com.mcqbuddy.attempt.service.AttemptService;
import com.mcqbuddy.bean.entity.attempt.Attempt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/attempt-api/attempts")
public class AttemptRestAPIController {

    private final AttemptService attemptService;
    private final AttemptExamOrchestrationService orchestrationService;

    public AttemptRestAPIController(AttemptService attemptService,
                                    AttemptExamOrchestrationService orchestrationService) {
        this.attemptService = attemptService;
        this.orchestrationService = orchestrationService;
    }

    @PostMapping(value = "")
    public ResponseEntity<?> create(@RequestBody Attempt attempt) {
        return ResponseEntity.ok(attemptService.create(attempt));
    }

    @GetMapping(value = "")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(attemptService.list());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<?> get(@PathVariable int id) {
        return ResponseEntity.ok(attemptService.get(id));
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody Attempt attempt) {
        return ResponseEntity.ok(attemptService.update(id, attempt));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        attemptService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/exams/{examId}/import-marking-scheme")
    public ResponseEntity<?> importMarkingSchemeFromExam(@PathVariable int examId) {
        return ResponseEntity.ok(orchestrationService.importMarkingSchemeFromExam(examId));
    }

    @PostMapping(value = "/answer-selections")
    public ResponseEntity<?> evaluateAndRecordAnswer(@RequestBody EvaluateAttemptAnswerRequest request) {
        return ResponseEntity.ok(orchestrationService.evaluateAndRecord(request));
    }

    @PostMapping(value = "/{attemptId}/finish")
    public ResponseEntity<?> finishAttempt(@PathVariable int attemptId) {
        return ResponseEntity.ok(orchestrationService.finishAttempt(attemptId));
    }
}
