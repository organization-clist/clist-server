package com.clist.global.ai.tool.handlers;

import com.clist.domain.feedback.entity.FeedbackMessage;
import com.clist.domain.feedback.entity.FeedbackSession;
import com.clist.domain.feedback.repository.FeedbackMessageRepository;
import com.clist.domain.feedback.repository.FeedbackSessionRepository;
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
public class GetFeedbackMessagesHandler implements ToolHandler {

    private final FeedbackSessionRepository feedbackSessionRepository;
    private final FeedbackMessageRepository feedbackMessageRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getFeedbackMessages";
    }

    @Override
    public String execute(User user, String argument) {
        try {
            List<FeedbackSession> sessions = feedbackSessionRepository.findAllByUser(user);

            List<Map<String, Object>> sessionData = sessions.stream().map(session -> {
                List<FeedbackMessage> messages = feedbackMessageRepository
                        .findAllByFeedbackSessionOrderByCreatedAtAsc(session);

                List<Map<String, Object>> msgData = messages.stream().map(m -> Map.<String, Object>of(
                        "role", m.getRole(),
                        "message", m.getMessage()
                )).toList();

                return Map.<String, Object>of(
                        "mdTitle", session.getMdDocument().getTitle(),
                        "status", session.getStatus(),
                        "summary", session.getSummary() != null ? session.getSummary() : "",
                        "messages", msgData
                );
            }).toList();

            return objectMapper.writeValueAsString(ToolResult.of(getName(), sessionData));
        } catch (Exception e) {
            log.error("GetFeedbackMessagesHandler error: ", e);
            return "{\"error\": \"피드백 기록 조회 실패\"}";
        }
    }
}