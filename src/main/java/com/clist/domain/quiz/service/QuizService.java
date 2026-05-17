package com.clist.domain.quiz.service;

import com.clist.domain.history.entity.LearningHistoryList;
import com.clist.domain.history.repository.LearningHistoryListRepository;
import com.clist.domain.history.repository.LearningHistoryRepository;
import com.clist.domain.md.entity.MdDocument;
import com.clist.domain.md.repository.MdDocumentRepository;
import com.clist.domain.quiz.dto.QuizDto;
import com.clist.domain.quiz.entity.QuizQuestion;
import com.clist.domain.quiz.entity.QuizSession;
import com.clist.domain.quiz.repository.QuizQuestionRepository;
import com.clist.domain.quiz.repository.QuizSessionRepository;
import com.clist.domain.user.entity.User;
import com.clist.domain.user.repository.UserRepository;
import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.clist.global.util.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {
    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final MdDocumentRepository mdDocumentRepository;
    private final UserRepository userRepository;
    private final LearningHistoryRepository learningHistoryRepository;
    private final LearningHistoryListRepository learningHistoryListRepository;
    private final QuizAiService quizAiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuizDto.SessionDetailResponse createSession(QuizDto.SessionCreateRequest request) {
        User user = getCurrentUser();

        quizSessionRepository.findActiveSessionByUser(user).ifPresent(s -> {
            throw new CustomException(ErrorCode.QUIZ_ACTIVE_SESSION_EXISTS.getStatus(), ErrorCode.QUIZ_ACTIVE_SESSION_EXISTS.getMessage());
        });

        MdDocument md = mdDocumentRepository.findByUserAndTitle(user, request.getMdTitle())
                .orElseThrow(() -> new CustomException(ErrorCode.MD_NOT_FOUND.getStatus(), ErrorCode.MD_NOT_FOUND.getMessage()));

        // 이미 학습한 항목 수집 (중복 제외용)
        List<String> learnedTopics = getLearnedTopics(user);

        QuizSession session = QuizSession.builder()
                .user(user)
                .mdDocument(md)
                .status("ACTIVE")
                .build();
        quizSessionRepository.save(session);

        List<QuizAiService.QuizItem> items = quizAiService.generateQuestions(
                md.getContent(), learnedTopics
        );

        List<QuizQuestion> questions = items.stream()
                .map(item -> QuizQuestion.builder()
                        .quizSession(session)
                        .question(item.question())
                        .answer(item.answer())
                        .build())
                .toList();
        quizQuestionRepository.saveAll(questions);

        return new QuizDto.SessionDetailResponse(session, questions);
    }

    private List<String> getLearnedTopics(User user) {
        return learningHistoryRepository.findByUser(user)
                .map(history -> learningHistoryListRepository.findAllByLearningHistory(history)
                        .stream()
                        .map(LearningHistoryList::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.SessionResponse> getAllSessions() {
        User user = getCurrentUser();
        return quizSessionRepository.findAllByUser(user).stream()
                .map(QuizDto.SessionResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public List<QuizDto.SessionResponse> getSessionsByMdTitle(String mdTitle) {
        User user = getCurrentUser();
        return quizSessionRepository.findAllByUserAndMdDocument_Title(user, mdTitle).stream()
                .map(QuizDto.SessionResponse::new).toList();
    }

    public Flux<ServerSentEvent<String>> submitAnswerStream(QuizDto.AnswerRequest request) {
        // DB 조회를 별도 트랜잭션 메서드로 분리
        QuizAnswerContext ctx = prepareAnswerContext();

        StringBuilder fullResponse = new StringBuilder();
        String[] doneDataHolder = new String[1];

        return quizAiService.gradeAndStream(ctx.question().getQuestion(), ctx.question().getAnswer(), request.getAnswer())
                .map(sse -> {
                    if ("chunk".equals(sse.event()) && sse.data() != null) {
                        fullResponse.append(sse.data());
                    }
                    if ("done".equals(sse.event())) {
                        boolean correct = parseCorrect(sse.data());
                        String nextQuestion = quizQuestionRepository
                                .findFirstUnansweredBySession(ctx.session())
                                .map(QuizQuestion::getQuestion)
                                .orElse(null);
                        try {
                            String doneData = objectMapper.writeValueAsString(
                                    java.util.Map.of("correct", correct,
                                            "nextQuestion", nextQuestion != null ? nextQuestion : "")
                            );
                            doneDataHolder[0] = String.valueOf(correct);
                            return ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data(doneData)
                                    .build();
                        } catch (Exception e) {
                            return sse;
                        }
                    }
                    return sse;
                })
                .doOnComplete(() -> {
                    boolean correct = "true".equals(doneDataHolder[0]);
                    saveAnswer(ctx.session(), ctx.question(), request.getAnswer(), correct);
                });
    }

    @Transactional(readOnly = true)
    protected QuizAnswerContext prepareAnswerContext() {
        User user = getCurrentUser();
        QuizSession session = quizSessionRepository.findActiveSessionByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NO_ACTIVE_SESSION.getStatus(), ErrorCode.QUIZ_NO_ACTIVE_SESSION.getMessage()));
        QuizQuestion question = quizQuestionRepository.findFirstUnansweredBySession(session)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_ALL_ANSWERED.getStatus(), ErrorCode.QUIZ_ALL_ANSWERED.getMessage()));
        return new QuizAnswerContext(session, question);
    }

    @Transactional
    protected void saveAnswer(QuizSession session, QuizQuestion question, String userAnswer, boolean correct) {
        question.submitAnswer(userAnswer, correct);
        quizQuestionRepository.save(question);
    }

    public record QuizAnswerContext(QuizSession session, QuizQuestion question) {}

    /**
     * 세션 종료: questions 삭제 + summary 저장
     */
    @Transactional
    public QuizDto.SessionResponse closeSession() {
        User user = getCurrentUser();

        QuizSession session = quizSessionRepository.findActiveSessionByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NO_ACTIVE_SESSION.getStatus(), ErrorCode.QUIZ_NO_ACTIVE_SESSION.getMessage()));

        List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizSession(session);

        // summary 생성
        String summary = quizAiService.generateSummary(session.getMdDocument().getTitle(), questions);

        // questions 삭제
        quizQuestionRepository.deleteAll(questions);

        // session summary 저장 + 상태 변경
        session.close(summary);
        quizSessionRepository.save(session);

        return new QuizDto.SessionResponse(session);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        User user = getCurrentUser();
        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_SESSION_NOT_FOUND.getStatus(), ErrorCode.QUIZ_SESSION_NOT_FOUND.getMessage()));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN.getStatus(), ErrorCode.FORBIDDEN.getMessage());
        }
        quizSessionRepository.delete(session);
    }

    private boolean parseCorrect(String doneData) {
        try {
            JsonNode node = objectMapper.readTree(doneData);
            return node.has("correct") && node.get("correct").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private User getCurrentUser() {
        UUID userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND.getStatus(), ErrorCode.USER_NOT_FOUND.getMessage()));
    }
}