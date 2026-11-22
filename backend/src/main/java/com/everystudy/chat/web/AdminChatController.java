package com.everystudy.chat.web;

import com.everystudy.chat.service.ChatModerationService;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자의 채팅 메시지 삭제 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@RequestMapping("/api/v1/admin/chat-messages")
class AdminChatController {

    /** 삭제 사유. 필수다. */
    record DeleteRequest(@NotBlank @Size(max = 200) String note) {
    }

    private final ChatModerationService moderation;

    AdminChatController(ChatModerationService moderation) {
        this.moderation = moderation;
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody DeleteRequest request) {
        moderation.delete(user.userId(), id, request.note().strip());
        return ResponseEntity.noContent().build();
    }
}
