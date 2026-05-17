package com.clist.domain.feedback.controller;

import com.clist.domain.feedback.dto.FeedbackDto;
import com.clist.domain.feedback.service.FeedbackService;
import com.clist.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<FeedbackDto.SessionResponse>> createSession(@RequestBody FeedbackDto.SessionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.createSession(request)));
    }

    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessage(@RequestBody FeedbackDto.MessageRequest request) {
        return feedbackService.sendMessageStream(request);
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<ApiResponse<FeedbackDto.SessionDetailResponse>> getSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getSession(id)));
    }

    @PostMapping("/close")
    public ResponseEntity<ApiResponse<FeedbackDto.SessionResponse>> closeSession() {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.closeSession()));
    }

    @DeleteMapping("/session/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID id) {
        feedbackService.deleteSession(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}