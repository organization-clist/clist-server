package com.clist.global.ai.tool.handlers;

import com.clist.domain.md.entity.MdDocument;
import com.clist.domain.md.repository.MdDocumentRepository;
import com.clist.domain.user.entity.User;
import com.clist.global.ai.tool.ToolHandler;
import com.clist.global.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMdContentHandler implements ToolHandler {

    private final MdDocumentRepository mdDocumentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getMdContent";
    }

    @Override
    public String execute(User user, String argument) {
        try {
            Optional<MdDocument> md = mdDocumentRepository.findByUserAndTitle(user, argument);
            Object data = md.<Object>map(doc -> Map.of(
                    "title", doc.getTitle(),
                    "content", doc.getContent() != null ? doc.getContent() : ""
            )).orElse(Map.of("error", "MD 문서를 찾을 수 없습니다: " + argument));

            return objectMapper.writeValueAsString(ToolResult.of(getName(), data));
        } catch (Exception e) {
            log.error("GetMdContentHandler error: ", e);
            return "{\"error\": \"MD 조회 실패\"}";
        }
    }
}