package com.everystudy.trust.repository;

import com.everystudy.trust.domain.HostPenaltyEvent;
import com.everystudy.trust.domain.HostPenaltyReason;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostPenaltyEventRepository extends JpaRepository<HostPenaltyEvent, Long> {

    boolean existsByStudyIdAndHostIdAndReason(Long studyId, Long hostId, HostPenaltyReason reason);
}
