package com.everystudy.chat.repository;

import com.everystudy.chat.domain.ChatNotice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatNoticeRepository extends JpaRepository<ChatNotice, Long> {
}
