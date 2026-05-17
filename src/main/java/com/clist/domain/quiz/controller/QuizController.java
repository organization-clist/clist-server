package com.clist.domain.quiz.controller;

import com.clist.domain.quiz.dto.QuizDto;
import com.clist.domain.quiz.service.QuizService;
import com.clist.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<QuizDto.SessionDetailResponse>> createSession(@RequestBody QuizDto.SessionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(quizService.createSession(request)));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<List<QuizDto.SessionResponse>>> getAllSessions() {
        return ResponseEntity.ok(ApiResponse.success(quizService.getAllSessions()));
    }

    @GetMapping("/session/{mdTitle}")
    public ResponseEntity<ApiResponse<List<QuizDto.SessionResponse>>> getSessionsByMdTitle(@PathVariable String mdTitle) {
        return ResponseEntity.ok(ApiResponse.success(quizService.getSessionsByMdTitle(mdTitle)));
    }

    @PostMapping(value = "/answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> submitAnswer(@RequestBody QuizDto.AnswerRequest request) {
        return quizService.submitAnswerStream(request);
    }

    /**
     * 세션 종료: quiz_questions 삭제 + quiz_sessions summary 저장
     */
    @PostMapping("/close")
    public ResponseEntity<ApiResponse<QuizDto.SessionResponse>> closeSession() {
        return ResponseEntity.ok(ApiResponse.success(quizService.closeSession()));
    }

    @DeleteMapping("/session/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID id) {
        quizService.deleteSession(id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}