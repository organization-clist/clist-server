package com.clist.domain.feedback.service;

import com.clist.domain.feedback.dto.FeedbackDto;
import com.clist.domain.feedback.entity.FeedbackMessage;
import com.clist.domain.feedback.entity.FeedbackSession;
import com.clist.domain.feedback.repository.FeedbackMessageRepository;
import com.clist.domain.feedback.repository.FeedbackSessionRepository;
import com.clist.domain.md.entity.MdDocument;
import com.clist.domain.md.repository.MdDocumentRepository;
import com.clist.domain.user.entity.User;
import com.clist.domain.user.repository.UserRepository;
import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.clist.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackSessionRepository feedbackSessionRepository;
    private final FeedbackMessageRepository feedbackMessageRepository;
    private final MdDocumentRepository mdDocumentRepository;
    private final UserRepository userRepository;
    private final FeedbackAiService feedbackAiService;

    @Transactional
    public FeedbackDto.SessionResponse createSession(FeedbackDto.SessionCreateRequest request) {
        User user = getCurrentUser();

        feedbackSessionRepository.findActiveSessionByUser(user).ifPresent(s -> {
            throw new CustomException(ErrorCode.FEEDBACK_ACTIVE_SESSION_EXISTS.getStatus(), ErrorCode.FEEDBACK_ACTIVE_SESSION_EXISTS.getMessage());
        });

        MdDocument md = mdDocumentRepository.findByUserAndTitle(user, request.getMdTitle())
                .orElseThrow(() -> new CustomException(ErrorCode.MD_NOT_FOUND.getStatus(), ErrorCode.MD_NOT_FOUND.getMessage()));

        FeedbackSession session = FeedbackSession.builder()
                .user(user)
                .mdDocument(md)
                .status("ACTIVE")
                .build();

        return new FeedbackDto.SessionResponse(feedbackSessionRepository.save(session));
    }

    public Flux<ServerSentEvent<String>> sendMessageStream(FeedbackDto.MessageRequest request) {
        User user = getCurrentUser();

        FeedbackSession session = feedbackSessionRepository.findActiveSessionByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NO_ACTIVE_SESSION.getStatus(), ErrorCode.FEEDBACK_NO_ACTIVE_SESSION.getMessage()));

        List<FeedbackMessage> history = feedbackMessageRepository
                .findAllByFeedbackSessionOrderByCreatedAtAsc(session);

        FeedbackMessage userMessage = FeedbackMessage.builder()
                .feedbackSession(session)
                .role("user")
                .message(request.getMessage())
                .build();
        feedbackMessageRepository.save(userMessage);

        StringBuilder fullResponse = new StringBuilder();

        return feedbackAiService.replyStream(session.getMdDocument().getContent(), history, request.getMessage())
                .map(sse -> {
                    if ("chunk".equals(sse.event()) && sse.data() != null) {
                        fullResponse.append(sse.data());
                    }
                    return sse;
                })
                .doOnComplete(() -> {
                    if (!fullResponse.isEmpty()) {
                        FeedbackMessage assistantMessage = FeedbackMessage.builder()
                                .feedbackSession(session)
                                .role("assistant")
                                .message(fullResponse.toString())
                                .build();
                        feedbackMessageRepository.save(assistantMessage);
                    }
                });
    }

    @Transactional
    public void saveAssistantMessage(FeedbackSession session, String content) {
        FeedbackMessage assistantMessage = FeedbackMessage.builder()
                .feedbackSession(session)
                .role("assistant")
                .message(content)
                .build();
        feedbackMessageRepository.save(assistantMessage);
    }

    @Transactional(readOnly = true)
    public FeedbackDto.SessionDetailResponse getSession(UUID sessionId) {
        User user = getCurrentUser();

        FeedbackSession session = feedbackSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_SESSION_NOT_FOUND.getStatus(), ErrorCode.FEEDBACK_SESSION_NOT_FOUND.getMessage()));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN.getStatus(), ErrorCode.FORBIDDEN.getMessage());
        }

        List<FeedbackMessage> messages = feedbackMessageRepository
                .findAllByFeedbackSessionOrderByCreatedAtAsc(session);

        return new FeedbackDto.SessionDetailResponse(session, messages);
    }

    /**
     * 세션 종료: summary 저장 + status CLOSED
     */
    @Transactional
    public FeedbackDto.SessionResponse closeSession() {
        User user = getCurrentUser();

        FeedbackSession session = feedbackSessionRepository.findActiveSessionByUser(user)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NO_ACTIVE_SESSION.getStatus(), ErrorCode.FEEDBACK_NO_ACTIVE_SESSION.getMessage()));

        List<FeedbackMessage> messages = feedbackMessageRepository
                .findAllByFeedbackSessionOrderByCreatedAtAsc(session);

        String summary = messages.isEmpty()
                ? "대화 내역이 없습니다."
                : feedbackAiService.generateSummary(session.getMdDocument().getTitle(), messages);

        session.close(summary);
        feedbackSessionRepository.save(session);

        return new FeedbackDto.SessionResponse(session);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        User user = getCurrentUser();

        FeedbackSession session = feedbackSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_SESSION_NOT_FOUND.getStatus(), ErrorCode.FEEDBACK_SESSION_NOT_FOUND.getMessage()));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN.getStatus(), ErrorCode.FORBIDDEN.getMessage());
        }

        feedbackSessionRepository.delete(session);
    }

    private User getCurrentUser() {
        UUID userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND.getStatus(), ErrorCode.USER_NOT_FOUND.getMessage()));
    }
}