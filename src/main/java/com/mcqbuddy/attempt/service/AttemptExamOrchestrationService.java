package com.mcqbuddy.attempt.service;

import com.mcqbuddy.attempt.api.dto.EvaluateAttemptAnswerRequest;
import com.mcqbuddy.attempt.api.dto.EvaluateAttemptAnswerResponse;
import com.mcqbuddy.attempt.api.dto.FinishAttemptResponse;
import com.mcqbuddy.attempt.api.dto.ImportMarkingSchemeResponse;
import com.mcqbuddy.attempt.api.dto.StartAttemptResponse;
import com.mcqbuddy.attempt.repository.AttemptRepository;
import com.mcqbuddy.attempt.repository.MarkingSchemeRepository;
import com.mcqbuddy.bean.entity.attempt.Attempt;
import com.mcqbuddy.bean.entity.attempt.AttemptAnswer;
import com.mcqbuddy.bean.entity.attempt.AttemptQuestion;
import com.mcqbuddy.bean.entity.markingscheme.CorrectAnswer;
import com.mcqbuddy.bean.entity.markingscheme.MarkingScheme;
import com.mcqbuddy.bean.entity.markingscheme.Question;
import com.mcqbuddy.attempt.security.BearerRoleChecker;
import com.mcqbuddy.common.ExamPublicKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttemptExamOrchestrationService {

    private final MarkingSchemeRepository markingSchemeRepository;
    private final AttemptRepository attemptRepository;
    private final BearerRoleChecker bearerRoleChecker;
    private final ExamInstituteAccessService examInstituteAccessService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String examServiceBaseUrl;

    public AttemptExamOrchestrationService(
            MarkingSchemeRepository markingSchemeRepository,
            AttemptRepository attemptRepository,
            BearerRoleChecker bearerRoleChecker,
            ExamInstituteAccessService examInstituteAccessService,
            @Value("${exam.service.base-url:http://localhost:8092}") String examServiceBaseUrl) {
        this.markingSchemeRepository = markingSchemeRepository;
        this.attemptRepository = attemptRepository;
        this.bearerRoleChecker = bearerRoleChecker;
        this.examInstituteAccessService = examInstituteAccessService;
        this.examServiceBaseUrl = examServiceBaseUrl;
    }

    public ImportMarkingSchemeResponse importMarkingSchemeFromExam(String examPublicKeyRaw, String authorizationHeader) {
        String examPk = requireExamPublicKey(examPublicKeyRaw);
        ExamPaperPayload exam = fetchExam(examPk, authorizationHeader);
        ensureGuestMayAccessExam(exam, authorizationHeader);

        List<ExamQuestionPayload> examQuestions = new ArrayList<>(exam.questions() == null ? List.of() : exam.questions());
        examQuestions.sort(Comparator.comparingInt(q -> q.questionNumber() == null ? 0 : q.questionNumber()));

        MarkingScheme markingScheme =
                markingSchemeRepository.findDetailByExamPublicKeyIgnoreCase(examPk).orElseGet(MarkingScheme::new);
        markingScheme.setExamPublicKey(examPk);

        // Replace existing rows for this exam to keep schema aligned with source exam.
        markingScheme.getQuestions().clear();
        int importedCorrectAnswers = 0;

        for (int i = 0; i < examQuestions.size(); i++) {
            ExamQuestionPayload examQuestion = examQuestions.get(i);
            Integer questionNumber = examQuestion.questionNumber() != null ? examQuestion.questionNumber() : i + 1;
            Question msQuestion = new Question();
            msQuestion.setQuestionNumber(questionNumber);
            markingScheme.addQuestion(msQuestion);

            List<ExamAnswerPayload> correctAnswers = (examQuestion.examAnswers() == null ? List.<ExamAnswerPayload>of() : examQuestion.examAnswers())
                    .stream()
                    .filter(a -> Boolean.TRUE.equals(a.correct()))
                    .collect(Collectors.toList());

            if (correctAnswers.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Exam question " + questionNumber + " has no correct option to import.");
            }
            if (correctAnswers.size() > 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Exam question " + questionNumber + " has multiple correct options; expected one.");
            }

            ExamAnswerPayload correct = correctAnswers.get(0);
            if (correct.optionNumber() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Exam question " + questionNumber + " has a correct answer without option number.");
            }

            CorrectAnswer msCorrect = new CorrectAnswer();
            msCorrect.setOptionNumber(correct.optionNumber());
            msQuestion.addCorrectAnswer(msCorrect);
            importedCorrectAnswers++;
        }

        normalizeMarkingScheme(markingScheme);
        MarkingScheme saved = markingSchemeRepository.save(markingScheme);
        return new ImportMarkingSchemeResponse(
                saved.getId(),
                examPk,
                examQuestions.size(),
                importedCorrectAnswers
        );
    }

    public StartAttemptResponse startAttempt(String examPublicKeyRaw, String authorizationHeader) {
        String examPk = requireExamPublicKey(examPublicKeyRaw);
        markingSchemeRepository
                .findDetailByExamPublicKeyIgnoreCase(examPk)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Marking scheme for exam " + examPk + " not found. Import it first."));

        ExamPaperPayload exam = fetchExam(examPk, authorizationHeader);
        ensureGuestMayAccessExam(exam, authorizationHeader);
        Instant startedAt = Instant.now();

        Attempt attempt = new Attempt();
        attempt.setExamPublicKey(examPk);
        attempt.setStartedAt(startedAt);
        attempt.setTotalTimeSeconds(exam.totalTime() == null ? null : exam.totalTime() * 60);

        Attempt saved = attemptRepository.save(attempt);
        return new StartAttemptResponse(
                saved.getId(),
                examPk,
                saved.getStartedAt(),
                saved.getTotalTimeSeconds()
        );
    }

    public EvaluateAttemptAnswerResponse evaluateAndRecord(EvaluateAttemptAnswerRequest request) {
        if (request == null
                || request.attemptId() == null
                || request.examPublicKey() == null
                || request.examPublicKey().isBlank()
                || request.questionNumber() == null
                || request.selectedOptionNumber() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "attemptId, examPublicKey, questionNumber, and selectedOptionNumber are required.");
        }

        String examPk = ExamPublicKeys.normalize(request.examPublicKey());
        int questionNumber = request.questionNumber();
        int selectedOptionNumber = request.selectedOptionNumber();

        MarkingScheme markingScheme =
                markingSchemeRepository
                        .findDetailByExamPublicKeyIgnoreCase(examPk)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Marking scheme for exam " + examPk + " not found. Import it first."));

        Question msQuestion =
                markingScheme.getQuestions().stream()
                        .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == questionNumber)
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No marking-scheme entry for question " + questionNumber + "."));

        Integer correctOptionNumber =
                msQuestion.getCorrectAnswers().stream()
                        .map(CorrectAnswer::getOptionNumber)
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No correct option found for question " + questionNumber + "."));

        boolean isCorrect = selectedOptionNumber == correctOptionNumber;

        Attempt attempt =
                attemptRepository
                        .findDetailById(request.attemptId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));

        if (attempt.getExamPublicKey() == null || !attempt.getExamPublicKey().equalsIgnoreCase(examPk)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attemptId does not match examPublicKey.");
        }

        AttemptQuestion attemptQuestion =
                attempt.getAttemptQuestions().stream()
                        .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == questionNumber)
                        .findFirst()
                        .orElseGet(
                                () -> {
                                    AttemptQuestion q = new AttemptQuestion();
                                    q.setQuestionNumber(questionNumber);
                                    attempt.addQuestion(q);
                                    return q;
                                });

        Set<AttemptAnswer> existingAnswers = attemptQuestion.getAttemptAnswers();
        existingAnswers.clear();
        AttemptAnswer selectedAnswer = new AttemptAnswer();
        selectedAnswer.setOptionNumber(selectedOptionNumber);
        selectedAnswer.setAnswer(request.selectedAnswerText());
        selectedAnswer.setCorrect(isCorrect);
        attemptQuestion.addAnswer(selectedAnswer);

        normalizeAttempt(attempt);
        Attempt savedAttempt = attemptRepository.save(attempt);

        return new EvaluateAttemptAnswerResponse(
                savedAttempt.getId(),
                examPk,
                questionNumber,
                selectedOptionNumber,
                correctOptionNumber,
                isCorrect
        );
    }

    public FinishAttemptResponse finishAttempt(int attemptId) {
        Attempt attempt =
                attemptRepository.findDetailById(attemptId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));
        if (attempt.getExamPublicKey() == null || attempt.getExamPublicKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempt is missing examPublicKey.");
        }

        String examPk = attempt.getExamPublicKey();
        MarkingScheme markingScheme =
                markingSchemeRepository
                        .findDetailByExamPublicKeyIgnoreCase(examPk)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Marking scheme for exam " + examPk + " not found."));

        int totalQuestions = markingScheme.getQuestions() == null ? 0 : markingScheme.getQuestions().size();
        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marking scheme has no questions.");
        }

        int correctAnswers = 0;
        if (attempt.getAttemptQuestions() != null) {
            for (AttemptQuestion q : attempt.getAttemptQuestions()) {
                boolean questionCorrect =
                        q.getAttemptAnswers() != null
                                && q.getAttemptAnswers().stream().anyMatch(AttemptAnswer::isCorrect);
                if (questionCorrect) {
                    correctAnswers++;
                }
            }
        }

        double percentage = (correctAnswers * 100.0) / totalQuestions;
        return new FinishAttemptResponse(
                attempt.getId(),
                examPk,
                correctAnswers,
                totalQuestions,
                percentage
        );
    }

    private void ensureGuestMayAccessExam(ExamPaperPayload exam, String authorizationHeader) {
        if (Boolean.TRUE.equals(exam.publicExam())) {
            return;
        }
        if (bearerRoleChecker.isStaff(authorizationHeader)) {
            return;
        }
        String studentPublicId = bearerRoleChecker.callerPublicId(authorizationHeader);
        String examKey = exam.publicKey();
        if (studentPublicId != null
                && examKey != null
                && examInstituteAccessService.isExamAssignedToStudent(studentPublicId, examKey)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found.");
    }

    private static String requireExamPublicKey(String raw) {
        String pk = ExamPublicKeys.normalize(raw);
        if (pk == null || pk.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "examPublicKey is required.");
        }
        return pk;
    }

    @SuppressWarnings("null")
    private ExamPaperPayload fetchExam(String examPublicKey, String authorizationHeader) {
        String encodedPath = examPublicKey;
        String primary = examServiceBaseUrl + "/exam-api/exams/by-key/" + encodedPath;
        String dockerFallback = "http://exam-service:8092/exam-api/exams/by-key/" + encodedPath;
        String localFallback = "http://localhost:8092/exam-api/exams/by-key/" + encodedPath;

        List<String> candidates = new ArrayList<>();
        candidates.add(primary);
        if (!primary.equals(dockerFallback)) {
            candidates.add(dockerFallback);
        }
        if (!primary.equals(localFallback)) {
            candidates.add(localFallback);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader.trim());
        }
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        RestClientException lastException = null;
        for (String url : candidates) {
            try {
                var response =
                        restTemplate.exchange(
                                URI.create(url),
                                HttpMethod.GET,
                                requestEntity,
                                ExamPaperPayload.class);
                ExamPaperPayload exam = response.getBody();
                if (exam == null || exam.id() == null) {
                    continue;
                }
                return exam;
            } catch (HttpClientErrorException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found.", e);
            } catch (RestClientException e) {
                lastException = e;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to load exam from exam-service for public key " + examPublicKey + ". Tried base URLs: " + String.join(", ", candidates),
                lastException
        );
    }

    private static void normalizeAttempt(Attempt attempt) {
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

    private static void normalizeMarkingScheme(MarkingScheme ms) {
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

    private record ExamPaperPayload(
            Integer id,
            String publicKey,
            String name,
            String subject,
            String description,
            Integer totalTime,
            Boolean publicExam,
            List<ExamQuestionPayload> questions
    ) {
    }

    private record ExamQuestionPayload(
            Integer id,
            Integer questionNumber,
            String question,
            List<ExamAnswerPayload> examAnswers
    ) {
    }

    private record ExamAnswerPayload(
            Integer id,
            Integer optionNumber,
            String answer,
            Boolean correct
    ) {
    }
}
