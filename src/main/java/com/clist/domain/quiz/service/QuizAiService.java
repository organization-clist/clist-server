package com.clist.domain.quiz.service;

import com.clist.domain.quiz.entity.QuizQuestion;
import com.clist.global.ai.AIService;
import com.clist.global.ai.OpenAiDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAiService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    /**
     * @param learnedTopics 이미 학습한 항목 목록 (중복 제외)
     */
    public List<QuizItem> generateQuestions(String mdContent, List<String> learnedTopics) {
        String exclusion = learnedTopics.isEmpty()
                ? ""
                : "\n\n이미 학습한 항목이므로 아래 주제는 퀴즈에서 제외하세요:\n- " + String.join("\n- ", learnedTopics);

        String systemPrompt = """
                당신은 퀴즈 생성기입니다. 주어진 마크다운 내용을 바탕으로 1개의 퀴즈를 생성하세요.
                반드시 아래 JSON 형식으로만 응답하세요:
                {"questions": [{"question": "질문 내용", "answer": "정답 내용"}]}
                %s
                """.formatted(exclusion);

        List<OpenAiDto.Message> messages = List.of(
                OpenAiDto.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiDto.Message.builder().role("user").content(mdContent).build()
        );

        String response = aiService.chatWithTools(null, messages);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode questionsNode = root.get("questions");
            List<QuizItem> items = new ArrayList<>();
            for (JsonNode node : questionsNode) {
                items.add(new QuizItem(node.get("question").asText(), node.get("answer").asText()));
            }
            return items;
        } catch (Exception e) {
            log.error("퀴즈 파싱 실패: {}", response, e);
            throw new RuntimeException("퀴즈 생성 실패");
        }
    }

    public Flux<ServerSentEvent<String>> gradeAndStream(String question, String correctAnswer, String userAnswer) {
        String systemPrompt = """
                당신은 퀴즈 채점관입니다.
                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "correct": true 또는 false,
                  "summary": "정답이에요. [이유 설명...]" 또는 "오답이에요. [이유 설명...]"
                }
                - correct가 true이면 summary는 반드시 "정답이에요."로 시작하세요.
                - correct가 false이면 summary는 반드시 "오답이에요."로 시작하세요.
                - summary는 한국어로 작성하고, 이유를 친절하게 설명하세요.
                - 의미적으로 동일하면 정답으로 처리하세요.
                """;

        String userContent = "질문: %s\n정답: %s\n사용자 답변: %s"
                .formatted(question, correctAnswer, userAnswer);

        List<OpenAiDto.Message> messages = List.of(
                OpenAiDto.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiDto.Message.builder().role("user").content(userContent).build()
        );

        StringBuilder fullResponse = new StringBuilder();

        return aiService.streamResponse(messages)
                .map(sse -> {
                    if ("chunk".equals(sse.event()) && sse.data() != null) {
                        fullResponse.append(sse.data());
                    }
                    if ("done".equals(sse.event())) {
                        return ServerSentEvent.<String>builder()
                                .event("done")
                                .data(parseDoneData(fullResponse.toString()))
                                .build();
                    }
                    return sse;
                })
                .filter(sse -> !"done".equals(sse.event()) || sse.data() != null);
    }

    private String parseDoneData(String fullText) {
        try {
            int start = fullText.indexOf('{');
            int end = fullText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode node = objectMapper.readTree(fullText.substring(start, end + 1));
                boolean correct = node.has("correct") && node.get("correct").asBoolean();
                return objectMapper.writeValueAsString(java.util.Map.of("correct", correct));
            }
        } catch (Exception e) {
            log.warn("done data 파싱 실패: {}", fullText);
        }
        return "{\"correct\": false}";
    }

    public String generateSummary(String mdTitle, List<QuizQuestion> questions) {
        long correct = questions.stream().filter(q -> Boolean.TRUE.equals(q.getIsCorrect())).count();
        String systemPrompt = "퀴즈 결과를 한국어로 간단히 요약해주세요. 200자 이내로 작성하세요.";
        String userContent = "MD: %s | 전체: %d | 정답: %d | 오답: %d"
                .formatted(mdTitle, questions.size(), correct, questions.size() - correct);

        List<OpenAiDto.Message> messages = List.of(
                OpenAiDto.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiDto.Message.builder().role("user").content(userContent).build()
        );

        return aiService.chatWithTools(null, messages);
    }

    public record QuizItem(String question, String answer) {}
}