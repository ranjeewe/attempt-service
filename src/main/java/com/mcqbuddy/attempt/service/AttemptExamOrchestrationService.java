package com.mcqbuddy.attempt.service;

import com.mcqbuddy.attempt.api.dto.EvaluateAttemptAnswerRequest;
import com.mcqbuddy.attempt.api.dto.EvaluateAttemptAnswerResponse;
import com.mcqbuddy.attempt.api.dto.FinishAttemptResponse;
import com.mcqbuddy.attempt.api.dto.ImportMarkingSchemeResponse;
import com.mcqbuddy.attempt.repository.AttemptRepository;
import com.mcqbuddy.attempt.repository.MarkingSchemeRepository;
import com.mcqbuddy.bean.entity.attempt.Attempt;
import com.mcqbuddy.bean.entity.attempt.AttemptAnswer;
import com.mcqbuddy.bean.entity.attempt.AttemptQuestion;
import com.mcqbuddy.bean.entity.markingscheme.CorrectAnswer;
import com.mcqbuddy.bean.entity.markingscheme.MarkingScheme;
import com.mcqbuddy.bean.entity.markingscheme.Question;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttemptExamOrchestrationService {

    private final MarkingSchemeRepository markingSchemeRepository;
    private final AttemptRepository attemptRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String examServiceBaseUrl;

    public AttemptExamOrchestrationService(
            MarkingSchemeRepository markingSchemeRepository,
            AttemptRepository attemptRepository,
            @Value("${exam.service.base-url:http://localhost:8092}") String examServiceBaseUrl) {
        this.markingSchemeRepository = markingSchemeRepository;
        this.attemptRepository = attemptRepository;
        this.examServiceBaseUrl = examServiceBaseUrl;
    }

    public ImportMarkingSchemeResponse importMarkingSchemeFromExam(int examPaperId) {
        ExamPaperPayload exam = fetchExam(examPaperId);

        List<ExamQuestionPayload> examQuestions = new ArrayList<>(exam.questions() == null ? List.of() : exam.questions());
        examQuestions.sort(Comparator.comparingInt(q -> q.questionNumber() == null ? 0 : q.questionNumber()));

        MarkingScheme markingScheme = markingSchemeRepository.findDetailByExamPaperId(examPaperId)
                .orElseGet(MarkingScheme::new);
        markingScheme.setExamPaperId(examPaperId);

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
                examPaperId,
                examQuestions.size(),
                importedCorrectAnswers
        );
    }

    public EvaluateAttemptAnswerResponse evaluateAndRecord(EvaluateAttemptAnswerRequest request) {
        if (request == null
                || request.examPaperId() == null
                || request.questionNumber() == null
                || request.selectedOptionNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "examPaperId, questionNumber, and selectedOptionNumber are required.");
        }

        int examPaperId = request.examPaperId();
        int questionNumber = request.questionNumber();
        int selectedOptionNumber = request.selectedOptionNumber();

        MarkingScheme markingScheme = markingSchemeRepository.findDetailByExamPaperId(examPaperId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Marking scheme for exam " + examPaperId + " not found. Import it first."));

        Question msQuestion = markingScheme.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == questionNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No marking-scheme entry for question " + questionNumber + "."));

        Integer correctOptionNumber = msQuestion.getCorrectAnswers().stream()
                .map(CorrectAnswer::getOptionNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No correct option found for question " + questionNumber + "."));

        boolean isCorrect = selectedOptionNumber == correctOptionNumber;

        Attempt attempt = attemptRepository.findFirstByExamPaperIdOrderByIdDesc(examPaperId)
                .orElseGet(() -> {
                    Attempt created = new Attempt();
                    created.setExamPaperId(examPaperId);
                    return created;
                });

        AttemptQuestion attemptQuestion = attempt.getAttemptQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == questionNumber)
                .findFirst()
                .orElseGet(() -> {
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
                examPaperId,
                questionNumber,
                selectedOptionNumber,
                correctOptionNumber,
                isCorrect
        );
    }

    public FinishAttemptResponse finishAttempt(int attemptId) {
        Attempt attempt = attemptRepository.findDetailById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));
        if (attempt.getExamPaperId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempt is missing examPaperId.");
        }

        MarkingScheme markingScheme = markingSchemeRepository.findDetailByExamPaperId(attempt.getExamPaperId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Marking scheme for exam " + attempt.getExamPaperId() + " not found."));

        int totalQuestions = markingScheme.getQuestions() == null ? 0 : markingScheme.getQuestions().size();
        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marking scheme has no questions.");
        }

        int correctAnswers = 0;
        if (attempt.getAttemptQuestions() != null) {
            for (AttemptQuestion q : attempt.getAttemptQuestions()) {
                boolean questionCorrect = q.getAttemptAnswers() != null
                        && q.getAttemptAnswers().stream().anyMatch(AttemptAnswer::isCorrect);
                if (questionCorrect) {
                    correctAnswers++;
                }
            }
        }

        double percentage = (correctAnswers * 100.0) / totalQuestions;
        return new FinishAttemptResponse(
                attempt.getId(),
                attempt.getExamPaperId(),
                correctAnswers,
                totalQuestions,
                percentage
        );
    }

    @SuppressWarnings("null")
    private ExamPaperPayload fetchExam(int examPaperId) {
        String primary = examServiceBaseUrl + "/exam-api/exams/" + examPaperId;
        String dockerFallback = "http://exam-service:8092/exam-api/exams/" + examPaperId;
        String localFallback = "http://localhost:8092/exam-api/exams/" + examPaperId;

        List<String> candidates = new ArrayList<>();
        candidates.add(primary);
        if (!primary.equals(dockerFallback)) {
            candidates.add(dockerFallback);
        }
        if (!primary.equals(localFallback)) {
            candidates.add(localFallback);
        }

        RestClientException lastException = null;
        for (String url : candidates) {
            try {
                ExamPaperPayload exam = restTemplate.getForObject(URI.create(url), ExamPaperPayload.class);
                if (exam == null || exam.id() == null) {
                    continue;
                }
                return exam;
            } catch (RestClientException e) {
                lastException = e;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to load exam from exam-service for id " + examPaperId
                        + ". Tried base URLs: " + String.join(", ", candidates),
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
            String name,
            String subject,
            String description,
            Integer totalTime,
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
