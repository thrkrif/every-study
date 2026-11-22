package com.everystudy.chat.web;

import com.everystudy.chat.service.BlockService;
import com.everystudy.chat.service.ChatRoomService;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 채팅방 REST와 사용자 차단 (API 6장·7장). 실시간 송수신은 STOMP(T-047)다. */
@RestController
class ChatController {

    private final ChatRoomService chat;
    private final BlockService blocks;

    ChatController(ChatRoomService chat, BlockService blocks) {
        this.chat = chat;
        this.blocks = blocks;
    }

    @GetMapping("/api/v1/studies/{id}/chat")
    ChatRoomService.RoomView room(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return chat.room(id, user.userId());
    }

    @GetMapping("/api/v1/studies/{id}/chat/messages")
    ChatRoomService.MessagePage messages(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @RequestParam(required = false) @Positive Long before,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return chat.history(id, user.userId(), before, size);
    }

    @PutMapping("/api/v1/studies/{id}/chat/notice")
    ChatRoomService.Notice notice(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody NoticeRequest request) {
        return chat.updateNotice(id, user.userId(), request.content().strip());
    }

    @PutMapping("/api/v1/studies/{id}/chat/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody ReadRequest request) {
        chat.markRead(id, user.userId(), request.lastReadMessageId());
    }

    @PutMapping("/api/v1/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@AuthenticationPrincipal LoginUser user, @PathVariable long userId) {
        blocks.block(user.userId(), userId);
    }

    @DeleteMapping("/api/v1/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@AuthenticationPrincipal LoginUser user, @PathVariable long userId) {
        blocks.unblock(user.userId(), userId);
    }
}
