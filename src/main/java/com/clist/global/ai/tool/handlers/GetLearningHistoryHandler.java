package com.clist.global.ai.tool.handlers;

import com.clist.domain.history.entity.LearningHistory;
import com.clist.domain.history.entity.LearningHistoryList;
import com.clist.domain.history.repository.LearningHistoryListRepository;
import com.clist.domain.history.repository.LearningHistoryRepository;
import com.clist.domain.user.entity.User;
import com.clist.global.ai.tool.ToolHandler;
import com.clist.global.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetLearningHistoryHandler implements ToolHandler {

    private final LearningHistoryRepository learningHistoryRepository;
    private final LearningHistoryListRepository learningHistoryListRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getLearningHistory";
    }

    @Override
    public String execute(User user, String argument) {
        try {
            Optional<LearningHistory> historyOpt = learningHistoryRepository.findByUser(user);

            if (historyOpt.isEmpty()) {
                return objectMapper.writeValueAsString(ToolResult.of(getName(), List.of()));
            }

            List<LearningHistoryList> items = learningHistoryListRepository
                    .findAllByLearningHistory(historyOpt.get());

            List<Map<String, Object>> data = items.stream().map(item -> Map.<String, Object>of(
                    "name", item.getName() != null ? item.getName() : "",
                    "content", item.getContent() != null ? item.getContent() : ""
            )).toList();

            return objectMapper.writeValueAsString(ToolResult.of(getName(), data));
        } catch (Exception e) {
            log.error("GetLearningHistoryHandler error: ", e);
            return "{\"error\": \"학습 이력 조회 실패\"}";
        }
    }
}