package com.everystudy.notification.repository;

import com.everystudy.notification.domain.NotificationSetting;
import com.everystudy.notification.domain.NotificationSettingId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationSettingId> {

    List<NotificationSetting> findByUserId(Long userId);
}
