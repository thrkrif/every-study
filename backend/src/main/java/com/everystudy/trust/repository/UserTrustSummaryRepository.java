package com.everystudy.trust.repository;

import com.everystudy.trust.domain.UserTrustSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTrustSummaryRepository extends JpaRepository<UserTrustSummary, Long> {
}
