package com.clist.global.ai;

import com.clist.domain.user.entity.User;
import com.clist.global.ai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final AIClientRouter aiClientRouter;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Tool calling을 통해 단건 JSON 응답을 받아옴 (quiz 채점, history 업데이트 등)
     */
    public String chatWithTools(User user, List<OpenAiDto.Message> messages) {
        List<OpenAiDto.Message> conversation = new ArrayList<>(messages);

        // Tool call 응답이 오면 최대 5회까지 반복 실행
        for (int i = 0; i < 5; i++) {
            String response = aiClientRouter.chat(conversation);

            // tool call 요청인지 확인
            String toolName = extractToolName(response);
            if (toolName == null) {
                return response; // 최종 응답
            }

            if (!toolRegistry.has(toolName)) {
                log.warn("등록되지 않은 tool 요청: {}", toolName);
                return response;
            }

            String argument = extractToolArgument(response);
            String toolResult = toolRegistry.get(toolName).execute(user, argument);

            // tool 결과를 대화에 추가 후 재호출
            conversation.add(OpenAiDto.Message.builder()
                    .role("assistant")
                    .content(response)
                    .build());
            conversation.add(OpenAiDto.Message.builder()
                    .role("user")
                    .content("[TOOL_RESULT:" + toolName + "] " + toolResult)
                    .build());
        }

        return aiClientRouter.chat(conversation);
    }

    /**
     * SSE 스트리밍 응답
     * chunk 이벤트로 텍스트를 흘려보내고, done 이벤트로 완료 신호 전송
     */
    public Flux<ServerSentEvent<String>> streamResponse(List<OpenAiDto.Message> messages) {
        return aiClientRouter.stream(messages)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("chunk")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("done")
                        .build()))
                .onErrorResume(e -> {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "AI 오류 : 알 수 없는 오류가 발생했습니다";
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorMsg)
                            .build());
                });
    }

    /**
     * SSE 스트리밍 + 완료 후 done 이벤트에 JSON 데이터 포함
     */
    public Flux<ServerSentEvent<String>> streamWithDoneData(List<OpenAiDto.Message> messages, String doneData) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        aiClientRouter.stream(messages)
                .doOnNext(chunk -> sink.tryEmitNext(
                        ServerSentEvent.<String>builder()
                                .event("chunk")
                                .data(chunk)
                                .build()
                ))
                .doOnComplete(() -> {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(doneData)
                            .build());
                    sink.tryEmitComplete();
                })
                .doOnError(e -> {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "AI 오류 : 알 수 없는 오류가 발생했습니다";
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorMsg)
                            .build());
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux();
    }

    /**
     * Node 서버용 - 모든 유저 데이터를 한 번에 수집해서 메시지에 포함
     */
    public List<OpenAiDto.Message> buildContextMessages(User user, String systemPrompt, String userMessage) {
        StringBuilder context = new StringBuilder();

        // 모든 tool에서 데이터 수집
        for (String toolName : List.of("getMdContent", "getQuizHistory", "getFeedbackMessages", "getLearningHistory")) {
            if (toolRegistry.has(toolName)) {
                try {
                    String data = toolRegistry.get(toolName).execute(user, "");
                    context.append("[").append(toolName).append("]\n").append(data).append("\n\n");
                } catch (Exception e) {
                    log.warn("Context 수집 실패 - tool: {}", toolName);
                }
            }
        }

        return List.of(
                OpenAiDto.Message.builder()
                        .role("system")
                        .content(systemPrompt + "\n\n[USER_CONTEXT]\n" + context)
                        .build(),
                OpenAiDto.Message.builder()
                        .role("user")
                        .content(userMessage)
                        .build()
        );
    }

    private String extractToolName(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("tool_call")) {
                return node.get("tool_call").get("name").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractToolArgument(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("tool_call") && node.get("tool_call").has("argument")) {
                return node.get("tool_call").get("argument").asText();
            }
        } catch (Exception ignored) {}
        return "";
    }
}