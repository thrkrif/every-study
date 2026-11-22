package com.everystudy.chat.service;

import com.everystudy.chat.repository.ChatReadRepository;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.user.service.UserDirectory;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 차단 (PRD 4.12, API 7장). 차단한 사람의 채팅 메시지가 숨김으로 보인다. 둘 다 멱등이다. 자기 자신은 차단할 수
 * 없고({@code VALIDATION_FAILED}), 없는 사용자는 {@code NOT_FOUND} 다.
 */
@Service
public class BlockService {

    private final ChatReadRepository blocks;
    private final UserDirectory users;
    private final TimeProvider time;

    BlockService(ChatReadRepository blocks, UserDirectory users, TimeProvider time) {
        this.blocks = blocks;
        this.users = users;
        this.time = time;
    }

    @Transactional
    public void block(long blockerId, long targetId) {
        if (blockerId == targetId) {
            throw new InvalidInputException(List.of(new FieldViolation("userId", "자기 자신은 차단할 수 없습니다")));
        }
        if (users.findPublic(Set.of(targetId)).isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        blocks.block(blockerId, targetId, time.now());
    }

    @Transactional
    public void unblock(long blockerId, long targetId) {
        blocks.unblock(blockerId, targetId);
    }
}
