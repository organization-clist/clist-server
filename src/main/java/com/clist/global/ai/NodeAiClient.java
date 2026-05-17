package com.clist.global.ai;

import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import org.springframework.core.ParameterizedTypeReference;


import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeAiClient implements AIClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.node-server-url:http://localhost:3001}")
    private String nodeServerUrl;

    @Override
    public String chat(List<OpenAiDto.Message> messages) {
        try {
            Map<String, Object> body = Map.of("messages", messages);

            Map<?, ?> response = webClient.post()
                    .uri(nodeServerUrl + "/ai/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("content")) {
                throw new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), "AI 오류 : Node 서버에서 오류가 발생했습니다");
            }

            return (String) response.get("content");

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Node AI server chat failed: ", e);
            throw new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), "AI 오류 : Node 서버에서 오류가 발생했습니다");
        }
    }

    @Override
    public Flux<String> stream(List<OpenAiDto.Message> messages) {
        Map<String, Object> body = Map.of("messages", messages);

        return webClient.post()
                .uri(nodeServerUrl + "/ai/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null && !"[DONE]".equals(sse.data()))
                .map(ServerSentEvent::data)
                .filter(data -> !data.isBlank())
                .doOnError(e -> log.error("Node AI server stream failed: ", e))
                .onErrorMap(e -> new CustomException(
                        ErrorCode.AI_REQUEST_FAILED.getStatus(),
                        "AI 오류 : Node 서버에서 오류가 발생했습니다"));
    }
}