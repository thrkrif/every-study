package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatMessage;
import com.everystudy.chat.domain.ChatMessageKind;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 채팅 메시지 삭제 (PRD 4.11 조치 3, API 7장, PLAN T-055). 행은 남기고 내용을 비운 뒤 지운 시각만 기록한다. 이후
 * 이전 대화 조회는 {@code deleted: true} 와 빈 내용을 준다. 감사 로그에는 메시지 내용을 남기지 않는다.
 */
@Service
public class ChatModerationService {

    private final ChatMessageRepository messages;
    private final AuditLogger audit;
    private final TimeProvider time;

    ChatModerationService(ChatMessageRepository messages, AuditLogger audit, TimeProvider time) {
        this.messages = messages;
        this.audit = audit;
        this.time = time;
    }

    /**
     * 메시지를 지운다. 이미 지웠으면 아무것도 하지 않는다(멱등).
     *
     * @param note 삭제 사유. 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     * @throws BusinessException 없는 메시지·시스템 메시지는 {@code NOT_FOUND}
     */
    @Transactional
    public void delete(long operatorId, long messageId, String note) {
        ChatMessage message = messages.findById(messageId)
                .filter(m -> m.getKind() == ChatMessageKind.TEXT)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (message.isDeleted()) {
            return;
        }
        message.deleteByOperator(time.now());
        Map<String, Object> detail = new HashMap<>();
        detail.put("roomId", message.getRoomId());
        detail.put("senderId", message.getSenderId());
        detail.put("note", note);
        audit.record(operatorId, AuditAction.MESSAGE_DELETE, "MESSAGE", messageId, detail);
    }
}
