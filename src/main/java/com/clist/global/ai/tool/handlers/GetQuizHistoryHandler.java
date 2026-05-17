package com.clist.global.ai.tool.handlers;

import com.clist.domain.quiz.entity.QuizQuestion;
import com.clist.domain.quiz.entity.QuizSession;
import com.clist.domain.quiz.repository.QuizQuestionRepository;
import com.clist.domain.quiz.repository.QuizSessionRepository;
import com.clist.domain.user.entity.User;
import com.clist.global.ai.tool.ToolHandler;
import com.clist.global.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetQuizHistoryHandler implements ToolHandler {

    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getQuizHistory";
    }

    @Override
    public String execute(User user, String argument) {
        try {
            List<QuizSession> sessions = quizSessionRepository.findAllByUser(user);

            List<Map<String, Object>> sessionData = sessions.stream().map(session -> {
                List<QuizQuestion> questions = quizQuestionRepository.findAllByQuizSession(session);
                List<Map<String, Object>> questionData = questions.stream().map(q -> Map.<String, Object>of(
                        "question", q.getQuestion(),
                        "answer", q.getAnswer() != null ? q.getAnswer() : "",
                        "userAnswer", q.getUserAnswer() != null ? q.getUserAnswer() : "",
                        "isCorrect", q.getIsCorrect() != null ? q.getIsCorrect() : false
                )).toList();

                return Map.<String, Object>of(
                        "mdTitle", session.getMdDocument().getTitle(),
                        "status", session.getStatus(),
                        "summary", session.getSummary() != null ? session.getSummary() : "",
                        "questions", questionData
                );
            }).toList();

            return objectMapper.writeValueAsString(ToolResult.of(getName(), sessionData));
        } catch (Exception e) {
            log.error("GetQuizHistoryHandler error: ", e);
            return "{\"error\": \"퀴즈 기록 조회 실패\"}";
        }
    }
}