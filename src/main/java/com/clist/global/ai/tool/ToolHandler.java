package com.clist.global.ai.tool;

import com.clist.domain.user.entity.User;

public interface ToolHandler {
    String getName();
    String execute(User user, String argument);
}