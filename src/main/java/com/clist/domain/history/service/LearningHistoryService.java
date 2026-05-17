package com.clist.domain.history.service;

import com.clist.domain.feedback.entity.FeedbackSession;
import com.clist.domain.feedback.repository.FeedbackSessionRepository;
import com.clist.domain.history.dto.HistoryDto;
import com.clist.domain.history.entity.LearningHistory;
import com.clist.domain.history.entity.LearningHistoryList;
import com.clist.domain.history.repository.LearningHistoryListRepository;
import com.clist.domain.history.repository.LearningHistoryRepository;
import com.clist.domain.quiz.entity.QuizSession;
import com.clist.domain.quiz.repository.QuizSessionRepository;
import com.clist.domain.user.entity.User;
import com.clist.domain.user.repository.UserRepository;
import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.clist.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningHistoryService {

    private final LearningHistoryRepository learningHistoryRepository;
    private final LearningHistoryListRepository learningHistoryListRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final FeedbackSessionRepository feedbackSessionRepository;
    private final HistoryAiService historyAiService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public HistoryDto.Response getHistory() {
        User user = getCurrentUser();

        LearningHistory history = learningHistoryRepository.findByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.HISTORY_NOT_FOUND.getStatus(), ErrorCode.HISTORY_NOT_FOUND.getMessage()));

        List<LearningHistoryList> items = learningHistoryListRepository.findAllByLearningHistory(history);
        return new HistoryDto.Response(history, items);
    }

    /**
     * 종료된 quiz/feedback 세션의 summary를 기반으로
     * 세션 하나 당 learning_history_list 로우 하나 생성
     */
    @Transactional
    public HistoryDto.Response update() {
        User user = getCurrentUser();

        LearningHistory history = learningHistoryRepository.findByUser(user)
                .orElseGet(() -> learningHistoryRepository.save(
                        LearningHistory.builder().user(user).build()
                ));

        List<LearningHistoryList> newItems = new ArrayList<>();

        // 종료된 퀴즈 세션 처리
        List<QuizSession> closedQuizSessions = quizSessionRepository.findAllByUser(user)
                .stream()
                .filter(s -> "CLOSED".equals(s.getStatus()) && s.getSummary() != null)
                .toList();

        for (QuizSession session : closedQuizSessions) {
            List<HistoryDto.ItemRequest> items = historyAiService.generateHistoryItems(
                    session.getSummary(), null, session.getMdDocument().getTitle()
            );
            for (HistoryDto.ItemRequest item : items) {
                newItems.add(LearningHistoryList.builder()
                        .learningHistory(history)
                        .name(item.getName())
                        .content(item.getContent())
                        .build());
            }
        }

        // 종료된 피드백 세션 처리
        List<FeedbackSession> closedFeedbackSessions = feedbackSessionRepository.findAllByUser(user)
                .stream()
                .filter(s -> "CLOSED".equals(s.getStatus()) && s.getSummary() != null)
                .toList();

        for (FeedbackSession session : closedFeedbackSessions) {
            List<HistoryDto.ItemRequest> items = historyAiService.generateHistoryItems(
                    null, session.getSummary(), session.getMdDocument().getTitle()
            );
            for (HistoryDto.ItemRequest item : items) {
                newItems.add(LearningHistoryList.builder()
                        .learningHistory(history)
                        .name(item.getName())
                        .content(item.getContent())
                        .build());
            }
        }

        learningHistoryListRepository.saveAll(newItems);

        List<LearningHistoryList> allItems = learningHistoryListRepository.findAllByLearningHistory(history);
        return new HistoryDto.Response(history, allItems);
    }

    @Transactional
    public void appendItems(User user, List<HistoryDto.ItemRequest> items) {
        LearningHistory history = learningHistoryRepository.findByUser(user)
                .orElseGet(() -> learningHistoryRepository.save(
                        LearningHistory.builder().user(user).build()
                ));

        List<LearningHistoryList> newItems = items.stream()
                .map(item -> LearningHistoryList.builder()
                        .learningHistory(history)
                        .name(item.getName())
                        .content(item.getContent())
                        .build())
                .toList();

        learningHistoryListRepository.saveAll(newItems);
    }

    private User getCurrentUser() {
        UUID userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND.getStatus(), ErrorCode.USER_NOT_FOUND.getMessage()));
    }
}