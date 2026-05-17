package com.clist.global.ai.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {
    private String tool;
    private Object data;
    private Meta meta;

    @Getter
    @Builder
    public static class Meta {
        private String source;
        private String timestamp;
    }

    public static ToolResult of(String toolName, Object data) {
        return ToolResult.builder()
                .tool(toolName)
                .data(data)
                .meta(Meta.builder()
                        .source("database")
                        .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build())
                .build();
    }
}