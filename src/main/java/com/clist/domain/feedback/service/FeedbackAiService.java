package com.clist.domain.feedback.service;

import com.clist.domain.feedback.entity.FeedbackMessage;
import com.clist.global.ai.AIService;
import com.clist.global.ai.OpenAiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackAiService {

    private final AIService aiService;

    /**
     * 피드백 답변 SSE 스트리밍
     */
    public Flux<ServerSentEvent<String>> replyStream(String mdContent, List<FeedbackMessage> history, String userMessage) {
        String systemPrompt = """
                당신은 마크다운 문서를 기반으로 학습을 도와주는 AI 어시스턴트입니다.
                문서 내용을 분석하고, 사용자의 질문에 친절하고 명확하게 한국어로 답변해주세요.
                """;

        List<OpenAiDto.Message> messages = new ArrayList<>();
        messages.add(OpenAiDto.Message.builder()
                .role("system")
                .content(systemPrompt + "\n\n[문서 내용]\n" + mdContent)
                .build());

        for (FeedbackMessage msg : history) {
            messages.add(OpenAiDto.Message.builder()
                    .role(msg.getRole())
                    .content(msg.getMessage())
                    .build());
        }

        messages.add(OpenAiDto.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        return aiService.streamResponse(messages);
    }

    public String generateSummary(String mdTitle, List<FeedbackMessage> messages) {
        String systemPrompt = "피드백 세션을 한국어로 간단히 요약해주세요. 200자 이내로 작성하세요.";

        StringBuilder conversation = new StringBuilder("MD: ").append(mdTitle).append("\n");
        for (FeedbackMessage msg : messages) {
            conversation.append("[").append(msg.getRole()).append("]: ").append(msg.getMessage()).append("\n");
        }

        List<OpenAiDto.Message> aiMessages = List.of(
                OpenAiDto.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiDto.Message.builder().role("user").content(conversation.toString()).build()
        );

        return aiService.chatWithTools(null, aiMessages);
    }
}