package com.clist.global.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIClientRouter implements AIClient {

    private final OpenAiClient openAiClient;
    private final NodeAiClient nodeAiClient;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${ai.use-ai-server:false}")
    private boolean useAiServer;

    private AIClient resolve() {
        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            log.debug("AI Client: OpenAI 직접 호출");
            return openAiClient;
        }
        if (useAiServer) {
            log.debug("AI Client: Node 서버 경유");
            return nodeAiClient;
        }
        throw new IllegalStateException("AI 설정이 없습니다. OPENAI_API_KEY 또는 USE_AI_SERVER=true 를 설정해주세요.");
    }

    @Override
    public String chat(List<OpenAiDto.Message> messages) {
        return resolve().chat(messages);
    }

    @Override
    public Flux<String> stream(List<OpenAiDto.Message> messages) {
        return resolve().stream(messages);
    }
}