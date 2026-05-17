package com.clist.domain.history.controller;

import com.clist.domain.history.dto.HistoryDto;
import com.clist.domain.history.service.LearningHistoryService;
import com.clist.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class LearningHistoryController {

    private final LearningHistoryService learningHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<HistoryDto.Response>> getHistory() {
        return ResponseEntity.ok(ApiResponse.success(learningHistoryService.getHistory()));
    }

    @PostMapping("/update")
    public ResponseEntity<ApiResponse<HistoryDto.Response>> update() {
        return ResponseEntity.ok(ApiResponse.success(learningHistoryService.update()));
    }
}