package com.clist.global.ai;

import reactor.core.publisher.Flux;

import java.util.List;

public interface AIClient {
    /**
     * 일반 단건 응답 (Tool calling, JSON 파싱용)
     */
    String chat(List<OpenAiDto.Message> messages);

    /**
     * SSE 스트리밍 응답
     */
    Flux<String> stream(List<OpenAiDto.Message> messages);
}