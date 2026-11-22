package com.everystudy.common.audit;

/**
 * 감사 로그에 남기는 행위 종류 (DATA-MODEL 9장, PLAN D-17). 이름이 {@code audit_log.action} 에 저장되므로
 * 이미 쓰인 이름은 바꾸지 않는다.
 *
 * <p>범위는 D-17 결정대로 <b>자동 감점·제재·판정 확정</b>과 <b>운영자 조치</b>다. 주최자는 출석을 바꿀 수 없으므로
 * 주최자의 수동 변경은 없다.
 */
public enum AuditAction {

    /** 주최자 감점 (자동). */
    HOST_PENALTY,
    /** 제재 부여 (자동 또는 운영자). */
    SANCTION_GRANT,
    /** 제재 해제 (운영자). */
    SANCTION_REVOKE,
    /** 노쇼 확정 (자동). */
    NO_SHOW_CONFIRM,
    /** 세션 무효 판정 (자동). */
    SESSION_VOID,
    /** 이의 결정 (운영자). */
    APPEAL_DECIDE,
    /** 출석 정정 (운영자). */
    ATTENDANCE_CORRECT,
    /** 신고 기각 (운영자). */
    REPORT_DISMISS,
    /** 스터디 강제 종료 (운영자). */
    STUDY_FORCE_END,
    /** 메시지 삭제 (운영자). */
    MESSAGE_DELETE
}
