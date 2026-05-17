package com.clist.global.ai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers;

    public ToolRegistry(List<ToolHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(ToolHandler::getName, Function.identity()));
        log.info("ToolRegistry 등록된 tools: {}", handlers.keySet());
    }

    public boolean has(String toolName) {
        return handlers.containsKey(toolName);
    }

    public ToolHandler get(String toolName) {
        ToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("등록되지 않은 tool: " + toolName);
        }
        return handler;
    }
}