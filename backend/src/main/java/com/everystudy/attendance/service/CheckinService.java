package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.Checkin;
import com.everystudy.attendance.domain.CheckinCode;
import com.everystudy.attendance.domain.CheckinMethod;
import com.everystudy.attendance.domain.CheckinResult;
import com.everystudy.attendance.repository.CheckinCodeRepository;
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
import java.security.SecureRandom;
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

    /** 발급한 코드와 만료 시각(체크인 마감). */
    public record IssuedCode(String code, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudySessions sessions;
    private final CheckinRepository checkins;
    private final CheckinCodeRepository codes;
    private final CheckinCodeAttempts attempts;
    private final AttendanceEventRecorder events;
    private final UserDirectory users;
    private final AttendanceProperties properties;
    private final TimeProvider time;

    CheckinService(StudySessions sessions, CheckinRepository checkins, CheckinCodeRepository codes,
            CheckinCodeAttempts attempts, AttendanceEventRecorder events, UserDirectory users,
            AttendanceProperties properties, TimeProvider time) {
        this.sessions = sessions;
        this.checkins = checkins;
        this.codes = codes;
        this.attempts = attempts;
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

        return record(session, userId, now, CheckinMethod.LOCATION, distance, accuracyM);
    }

    /**
     * 주최자가 일회용 코드를 발급(재발급)한다. 이전 코드는 무효가 된다. 코드는 체크인 마감 시각까지 유효하다. 검사 순서:
     * 스터디 없음 {@code NOT_FOUND} → 주최자 아님 {@code FORBIDDEN} → 종료·파투 {@code STUDY_CLOSED} → 시간창
     * {@code CHECKIN_WINDOW_CLOSED} → 주최자 미체크인 {@code HOST_NOT_CHECKED_IN} (PLAN X-28).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IssuedCode issueCode(long studyId, long userId) {
        Session session = sessions.lock(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (session.hostId() != userId || !session.activeMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!session.active()) {
            throw new BusinessException(StudyErrorCode.STUDY_CLOSED);
        }
        Instant now = time.now();
        CheckinRules.requireWindowOpen(now, session.checkinOpensAt(), session.checkinClosesAt());
        requireHostCheckedIn(studyId, session.hostId());

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        CheckinCode entity = codes.findById(studyId).orElse(null);
        if (entity == null) {
            entity = CheckinCode.issue(studyId, code, now, session.checkinClosesAt());
        } else {
            entity.reissue(code, now, session.checkinClosesAt());
        }
        codes.save(entity);
        return new IssuedCode(code, session.checkinClosesAt());
    }

    /**
     * 일회용 코드로 체크인한다. 위치 권한 거부·GPS 실패 시의 유일한 대체 수단이고, 출석·지각은 시각 기준으로 위치 체크인과
     * 똑같이 판정한다. 검사 순서: 스터디 없음 {@code NOT_FOUND} → 참여자 아님 {@code FORBIDDEN} → (이미 체크인했으면
     * 처음 결과) → 종료·파투 {@code STUDY_CLOSED} → 시간창 {@code CHECKIN_WINDOW_CLOSED} → 주최자 미체크인
     * {@code HOST_NOT_CHECKED_IN} → 코드 오류·만료·시도 초과 {@code CHECKIN_CODE_INVALID} (PLAN X-28).
     * 틀린 입력은 메모리의 실패 횟수에 남고, 횟수를 넘기면 맞는 코드도 거부한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result checkInByCode(long studyId, long userId, String input) {
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
        requireHostCheckedIn(studyId, session.hostId());

        boolean accepted = !attempts.isLocked(studyId, userId, properties.codeMaxFailures())
                && codes.findById(studyId).filter(code -> code.accepts(input, now)).isPresent();
        if (!accepted) {
            attempts.recordFailure(studyId, userId, now, session.checkinClosesAt());
            throw new BusinessException(AttendanceErrorCode.CHECKIN_CODE_INVALID);
        }
        return record(session, userId, now, CheckinMethod.CODE, null, null);
    }

    private void requireHostCheckedIn(long studyId, long hostId) {
        if (checkins.findByStudyIdAndUserId(studyId, hostId).isEmpty()) {
            throw new BusinessException(AttendanceErrorCode.HOST_NOT_CHECKED_IN);
        }
    }

    /** 체크인 행과 출석·지각 이벤트를 같은 트랜잭션에서 남긴다. 판정은 방식과 상관없이 시각 기준이다. */
    private Result record(Session session, long userId, Instant now, CheckinMethod method, Integer distance,
            Integer accuracyM) {
        long studyId = session.studyId();
        CheckinResult result = CheckinRules.resultAt(now, session.startAt(), properties.attendedWithinMinutes());
        checkins.save(Checkin.of(studyId, userId, now, method, result, distance, accuracyM));
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
