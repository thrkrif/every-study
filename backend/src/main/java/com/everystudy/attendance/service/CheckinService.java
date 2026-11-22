package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.Checkin;
import com.everystudy.attendance.domain.CheckinMethod;
import com.everystudy.attendance.domain.CheckinResult;
import com.everystudy.attendance.repository.CheckinRepository;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudyErrorCode;
import com.everystudy.study.service.StudySessions;
import com.everystudy.study.service.StudySessions.Session;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위치 체크인과 체크인 현황 (PRD 4.6, ARCHITECTURE 4.4, API 5장, PLAN T-027).
 *
 * <p>체크인은 {@code study} 행 락 안에서 처리하고 시간창은 <b>락을 잡은 뒤의 서버 시각</b>으로 검사한다. 그래서 마감 직전
 * 체크인과 자동 판정(T-031)이 겹쳐도 순서대로 실행되어 출석과 노쇼 후보가 함께 남지 않는다 (PLAN X-15). 격리 수준은 참여
 * 신청과 같은 이유로 {@code READ COMMITTED} 다.
 */
@Service
public class CheckinService {

    /** 체크인 결과. 코드 체크인이면 {@code distanceM} 이 없다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(CheckinResult result, Integer distanceM) {
    }

    /** 내 체크인 현황. 체크인하지 않았으면 {@code result}·{@code checkedInAt} 이 없다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Mine(boolean checkedIn, CheckinResult result, CheckinMethod method, Instant checkedInAt) {
    }

    /** 주최자가 보는 참여자 한 명의 체크인 여부. 판정 결과는 보이지 않는다 (주최자는 출석을 바꿀 수 없다, PRD 4.6). */
    public record MemberStatus(long userId, String nickname, boolean checkedIn) {
    }

    /**
     * 체크인 현황. 참여자는 {@code members} 없이 자기 것만, 주최자는 자기 것과 전원의 체크인 여부를 본다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attendance(Mine mine, List<MemberStatus> members) {
    }

    private final StudySessions sessions;
    private final CheckinRepository checkins;
    private final AttendanceEventRecorder events;
    private final UserDirectory users;
    private final AttendanceProperties properties;
    private final TimeProvider time;

    CheckinService(StudySessions sessions, CheckinRepository checkins, AttendanceEventRecorder events,
            UserDirectory users, AttendanceProperties properties, TimeProvider time) {
        this.sessions = sessions;
        this.checkins = checkins;
        this.events = events;
        this.users = users;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 위치로 체크인한다. 이미 했으면 처음 결과를 그대로 돌려준다(멱등). 검사 순서: 스터디 없음 {@code NOT_FOUND} → 참여자
     * 아님 {@code FORBIDDEN} → (이미 체크인했으면 처음 결과) → 종료·파투 {@code STUDY_CLOSED} → 시간창
     * {@code CHECKIN_WINDOW_CLOSED} → 반경 {@code CHECKIN_OUT_OF_RANGE}.
     *
     * @param accuracyM 클라이언트가 보고한 GPS 정확도(미터). 저장만 하고 판정에는 쓰지 않는다
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result checkInByLocation(long studyId, long userId, double latitude, double longitude, int accuracyM) {
        Session session = sessions.lock(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.activeMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        var existing = checkins.findByStudyIdAndUserId(studyId, userId);
        if (existing.isPresent()) {
            return resultOf(existing.get());
        }
        if (!session.active()) {
            throw new BusinessException(StudyErrorCode.STUDY_CLOSED);
        }
        Instant now = time.now();
        CheckinRules.requireWindowOpen(now, session.checkinOpensAt(), session.checkinClosesAt());
        int distance = CheckinRules.requireWithinRadius(latitude, longitude, session.latitude(), session.longitude(),
                properties.checkinRadiusMeters());

        CheckinResult result = CheckinRules.resultAt(now, session.startAt(), properties.attendedWithinMinutes());
        checkins.save(Checkin.of(studyId, userId, now, CheckinMethod.LOCATION, result, distance, accuracyM));
        if (result == CheckinResult.ATTENDED) {
            events.recordAttended(studyId, userId, now);
        } else {
            events.recordLate(studyId, userId, now);
        }
        return new Result(result, distance);
    }

    /**
     * 체크인 현황. 참여자(주최자 포함)만 볼 수 있다. 주최자에게는 전원의 체크인 여부가 더해진다.
     *
     * @throws BusinessException 없으면 {@code NOT_FOUND}, 참여자가 아니면 {@code FORBIDDEN}
     */
    @Transactional(readOnly = true)
    public Attendance attendance(long studyId, long userId) {
        Session session = sessions.read(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.activeMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        Map<Long, Checkin> byUser = checkins.findByStudyId(studyId).stream()
                .collect(Collectors.toMap(Checkin::getUserId, Function.identity()));
        Checkin mine = byUser.get(userId);
        Mine me = mine == null
                ? new Mine(false, null, null, null)
                : new Mine(true, mine.getResult(), mine.getMethod(), mine.getCheckedInAt());
        if (session.hostId() != userId) {
            return new Attendance(me, null);
        }
        List<Long> memberIds = sessions.activeMemberIds(studyId);
        Map<Long, PublicUser> people = users.findPublic(memberIds);
        List<MemberStatus> members = memberIds.stream()
                .map(id -> new MemberStatus(id, people.containsKey(id) ? people.get(id).nickname() : null,
                        byUser.containsKey(id)))
                .toList();
        return new Attendance(me, members);
    }

    private static Result resultOf(Checkin checkin) {
        return new Result(checkin.getResult(), checkin.getDistanceM());
    }
}
