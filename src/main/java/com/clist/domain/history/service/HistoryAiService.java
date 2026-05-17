package com.clist.domain.history.service;

import com.clist.domain.history.dto.HistoryDto;
import com.clist.global.ai.AIService;
import com.clist.global.ai.OpenAiDto;
import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryAiService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public List<HistoryDto.ItemRequest> generateHistoryItems(String quizSummary, String feedbackSummary, String mdTitle) {
        String systemPrompt = """
        당신은 학습 기록 분석가입니다. 퀴즈와 피드백 결과를 바탕으로 사용자가 학습한 내용을 추출하세요.
        
        반드시 아래 JSON 형식으로만 응답하세요:
        {"items": [{"name": "학습한 개념 이름", "content": "어떤 문제가 출제되었고, 사용자가 어떻게 답변했으며, 무엇을 이해했는지 한국어로 서술"}]}
        
        작성 규칙:
        - name: 학습한 개념이나 주제를 간결하게 작성
        - content: 구체적으로 어떤 질문이 있었고, 사용자가 어떤 답변을 했으며, 어떤 내용을 이해했는지 한국어로 서술
        - 마크다운 문법(#, **, - 등)은 절대 사용하지 말고 순수 한국어 텍스트로만 작성
        - 예시: "layout.tsx의 역할에 대한 문제에서 사용자는 여러 페이지에 공통 UI를 정의하는 파일이라고 답변하여 App Router의 레이아웃 구조를 이해하고 있음을 확인했다."
        """;

        String userContent = """
                MD 제목: %s
                퀴즈 요약: %s
                피드백 요약: %s
                """.formatted(
                mdTitle,
                quizSummary != null ? quizSummary : "없음",
                feedbackSummary != null ? feedbackSummary : "없음"
        );

        List<OpenAiDto.Message> messages = List.of(
                OpenAiDto.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiDto.Message.builder().role("user").content(userContent).build()
        );

        String response = aiService.chatWithTools(null, messages);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode itemsNode = root.get("items");
            List<HistoryDto.ItemRequest> items = new ArrayList<>();
            for (JsonNode node : itemsNode) {
                items.add(new HistoryDto.ItemRequest(
                        node.get("name").asText(),
                        node.get("content").asText()
                ));
            }
            return items;
        } catch (Exception e) {
            log.error("학습 이력 파싱 실패: {}", response, e);
            throw new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), ErrorCode.AI_REQUEST_FAILED.getMessage());
        }
    }
}