package com.everystudy.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/** 기록 전용. 조회·수정·삭제 API 는 만들지 않는다 (조회는 PLAN T-052). */
interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
