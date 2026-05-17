package com.clist.global.ai;

import com.clist.global.exception.CustomException;
import com.clist.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient implements AIClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Override
    public String chat(List<OpenAiDto.Message> messages) {
        try {
            boolean needsJson = messages.stream()
                    .anyMatch(m -> m.getContent() != null &&
                            m.getContent().toUpperCase().contains("JSON"));

            OpenAiDto.ChatRequest request = OpenAiDto.ChatRequest.builder()
                    .model(model)
                    .messages(messages)
                    .maxTokens(2000)
                    .responseFormat(needsJson ? Map.of("type", "json_object") : null)
                    .build();

            OpenAiDto.ChatResponse response = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiDto.ChatResponse.class)
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), "AI 오류 : OpenAI 서버에서 오류가 발생했습니다");
            }

            return response.getChoices().get(0).getMessage().getContent();

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI chat failed: ", e);
            throw new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), "AI 오류 : OpenAI 서버에서 오류가 발생했습니다");
        }
    }

    @Override
    public Flux<String> stream(List<OpenAiDto.Message> messages) {
        OpenAiDto.ChatRequest request = OpenAiDto.ChatRequest.builder()
                .model(model)
                .messages(messages)
                .maxTokens(2000)
                .stream(true)
                .build();

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null && !"[DONE]".equals(sse.data()))
                .map(ServerSentEvent::data)
                .flatMap(json -> {
                    try {
                        OpenAiDto.StreamChunk chunk = objectMapper.readValue(json, OpenAiDto.StreamChunk.class);
                        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                            OpenAiDto.StreamChunk.StreamChoice.Delta delta = chunk.getChoices().get(0).getDelta();
                            if (delta != null && delta.getContent() != null) {
                                return Flux.just(delta.getContent());
                            }
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .doOnError(e -> log.error("OpenAI stream failed: ", e))
                .onErrorMap(e -> new CustomException(ErrorCode.AI_REQUEST_FAILED.getStatus(), "AI 오류 : OpenAI 서버에서 오류가 발생했습니다"));
    }
}