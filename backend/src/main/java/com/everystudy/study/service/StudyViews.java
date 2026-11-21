package com.everystudy.study.service;

import com.everystudy.study.domain.Study;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 스터디 조회 응답 (API 4장). 엔티티를 그대로 내보내지 않는다 (ARCHITECTURE 3장). */
public final class StudyViews {

    private StudyViews() {
    }

    /** 내 상태. 클라이언트는 이것과 {@code recruiting} 으로 버튼을 정한다 (API 4장). */
    public enum MyState { NONE, MEMBER, WAITING, HOST }

    /**
     * 출석 지표 (API 3장 형식). 출석 집계(M3) 전까지는 모두 신규다 (PLAN T-009).
     *
     * @param attendanceRate 신규면 {@code null}
     */
    public record Trust(Integer attendanceRate, int windowCount, int noShow, int late, int lateCancel,
            boolean isNewcomer) {
        static final Trust NEWCOMER = new Trust(null, 0, 0, 0, 0, true);
    }

    /** 주최 지표 (API 3장 형식). 출석 집계(M3) 전까지는 0이고 횟수만 보여 준다. */
    public record HostStats(int hostedCount, int leftWithinWindowCount, boolean showCountOnly) {
        static final HostStats NONE_YET = new HostStats(0, 0, true);
    }

    /** @param nickname 탈퇴한 회원이면 {@code null} (화면은 "탈퇴한 사용자") */
    public record Host(long userId, String nickname, HostStats stats) {
    }

    public record Member(long userId, String nickname, Trust trust) {
    }

    /** 목록 카드 (PRD 4.3). */
    public record Summary(
            long id,
            String title,
            String topicCode,
            String districtCode,
            Instant startAt,
            int durationMin,
            int capacity,
            int memberCount,
            boolean recruiting,
            List<String> targetJobCodes,
            Study.EntryCriteria entryCriteria,
            Host host) {
    }

    /** @param nextCursor 다음 페이지가 없으면 {@code null} */
    public record Page(List<Summary> items, String nextCursor) {
    }

    /**
     * 상세 (PRD 4.3, API 4장).
     *
     * @param waitingCount 주최자에게만 값이 있다. 체크인 오픈 이후의 대기 행은 세지 않는다 (PLAN X-16)
     */
    public record Detail(
            long id,
            String title,
            String description,
            String topicCode,
            String districtCode,
            Study.Place place,
            Instant startAt,
            int durationMin,
            LocalDate sessionDate,
            String status,
            boolean recruiting,
            int capacity,
            int memberCount,
            List<String> targetJobCodes,
            Study.EntryCriteria entryCriteria,
            Host host,
            List<Member> members,
            MyState myState,
            boolean scheduleEditable,
            Instant checkinOpensAt,
            Instant checkinClosesAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer waitingCount) {
    }
}
