package com.everystudy.study.service;

import com.everystudy.common.code.JobCode;
import com.everystudy.common.code.RegionCode;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudySearchCondition;
import com.everystudy.study.repository.StudySearchCondition.Sort;
import com.everystudy.study.repository.StudySearchRepository;
import com.everystudy.study.repository.StudySummaryRow;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import com.everystudy.user.service.UserErrorCode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스터디 목록·상세·참여자 목록 (PRD 4.3, API 4장). */
@Service
public class StudyQueryService {

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudySearchRepository search;
    private final UserDirectory users;
    private final StudyProperties properties;
    private final TimeProvider time;

    StudyQueryService(StudyRepository studies, StudyMemberRepository members, StudySearchRepository search,
            UserDirectory users, StudyProperties properties, TimeProvider time) {
        this.studies = studies;
        this.members = members;
        this.search = search;
        this.users = users;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 목록 조건. 형식(코드, 범위)은 웹 계층이 검사했다.
     *
     * @param regionCode    시·도·시·구 어느 단계든. {@code null} 이면 전체
     * @param jobCode       희망 직군에 이 대분류가 명시된 스터디만
     * @param myJob         내 직군이 신청할 수 있는 스터디만 (로그인 필요)
     * @param includeClosed 시작 전인 마감 스터디도 보여 준다
     * @param sort          {@code latest}(최신순) 또는 {@code deadline}(마감임박순 = 시작이 빠른 순)
     */
    public record SearchQuery(String regionCode, String topicCode, String jobCode, boolean myJob,
            boolean includeClosed, String sort, String cursor, int size) {

        Sort sortOrder() {
            return "deadline".equals(sort) ? Sort.DEADLINE : Sort.LATEST;
        }
    }

    /**
     * 공개 목록. 이미 시작했거나 끝난 스터디는 보여 주지 않는다 (PLAN D-22).
     *
     * @param viewerId 로그인 회원. 비회원이면 {@code null}
     * @throws BusinessException {@code myJob} 인데 비로그인이면 {@code UNAUTHENTICATED}, 직군이 없으면 {@code ONBOARDING_REQUIRED}
     */
    @Transactional(readOnly = true)
    public StudyViews.Page search(SearchQuery query, Long viewerId) {
        Instant now = time.now();
        StudySearchCondition condition = new StudySearchCondition(
                query.regionCode() == null ? Set.of() : districtsOf(query.regionCode()),
                query.topicCode(),
                query.jobCode(),
                query.myJob() ? myJobCategory(viewerId) : null,
                query.includeClosed(),
                now,
                now.plusSeconds(properties.checkinOpensBeforeMinutes() * 60L),
                query.sortOrder(),
                StudyCursor.decode(query.cursor(), query.sortOrder()),
                query.size());

        List<StudySummaryRow> rows = search.search(condition);
        boolean hasNext = rows.size() > query.size();
        List<StudySummaryRow> page = hasNext ? rows.subList(0, query.size()) : rows;

        Map<Long, PublicUser> hosts = users.findPublic(page.stream().map(StudySummaryRow::hostId).toList());
        List<StudyViews.Summary> items = page.stream().map(row -> summary(row, hosts, now)).toList();
        String nextCursor = null;
        if (hasNext) {
            StudySummaryRow last = page.getLast();
            nextCursor = StudyCursor.encode(query.sortOrder(), last.id(), last.startAt());
        }
        return new StudyViews.Page(items, nextCursor);
    }

    /**
     * 상세. 비회원도 볼 수 있고, 종료·파투된 스터디도 주소로 열면 보인다.
     *
     * @throws BusinessException 없으면 {@code NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public StudyViews.Detail detail(long studyId, Long viewerId) {
        Study study = studies.findById(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<StudyMember> active = members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE);
        Map<Long, PublicUser> people = peopleOf(study, active);
        Instant now = time.now();

        StudyViews.MyState myState = myState(study, active, viewerId);
        boolean isHost = myState == StudyViews.MyState.HOST;
        int others = (int) active.stream().filter(member -> !member.getUserId().equals(study.getHostId())).count();

        return new StudyViews.Detail(
                study.getId(),
                study.getTitle(),
                study.getDescription(),
                study.getTopicCode(),
                study.getDistrictCode(),
                study.getPlace(),
                study.getStartAt(),
                study.getDurationMin(),
                study.getSessionDate(),
                study.getStatus().name(),
                StudyTimeline.isRecruiting(study.getStatus(), study.getStartAt(), active.size(), study.getCapacity(),
                        now, properties),
                study.getCapacity(),
                active.size(),
                study.getTargetJobCodes().stream().sorted().toList(),
                study.getEntryCriteria(),
                host(study.getHostId(), people),
                memberViews(active, people),
                myState,
                StudyTimeline.isScheduleEditable(study.getStatus(), study.getStartAt(), others, now),
                StudyTimeline.checkinOpensAt(study.getStartAt(), properties),
                StudyTimeline.checkinClosesAt(study.getStartAt(), properties),
                isHost ? 0 : null);
    }

    /**
     * 참여자 목록. 그 스터디의 {@code ACTIVE} 참여자(주최자 포함)만 볼 수 있다.
     *
     * @throws BusinessException 없으면 {@code NOT_FOUND}, 참여자가 아니면 {@code FORBIDDEN}
     */
    @Transactional(readOnly = true)
    public List<StudyViews.Member> members(long studyId, long viewerId) {
        Study study = studies.findById(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<StudyMember> active = members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE);
        if (active.stream().noneMatch(member -> member.getUserId() == viewerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return memberViews(active, peopleOf(study, active));
    }

    private Set<String> districtsOf(String regionCode) {
        Set<String> districts = new HashSet<>();
        RegionCode.find(regionCode).orElseThrow().districts().forEach(district -> districts.add(district.name()));
        return districts;
    }

    private String myJobCategory(Long viewerId) {
        if (viewerId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        PublicUser me = users.findPublic(List.of(viewerId)).get(viewerId);
        if (me == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return JobCode.find(me.jobCode())
                .map(job -> job.category().name())
                .orElseThrow(() -> new BusinessException(UserErrorCode.ONBOARDING_REQUIRED));
    }

    private StudyViews.Summary summary(StudySummaryRow row, Map<Long, PublicUser> hosts, Instant now) {
        return new StudyViews.Summary(row.id(), row.title(), row.topicCode(), row.districtCode(), row.startAt(),
                row.durationMin(), row.capacity(), row.memberCount(),
                StudyTimeline.isRecruiting(StudyStatus.ACTIVE, row.startAt(),
                        row.memberCount(), row.capacity(), now, properties),
                row.targetJobCodes(),
                new Study.EntryCriteria(row.minAttendanceRate(), row.allowNewcomer()),
                host(row.hostId(), hosts));
    }

    private Map<Long, PublicUser> peopleOf(Study study, List<StudyMember> active) {
        Set<Long> ids = new HashSet<>();
        ids.add(study.getHostId());
        active.forEach(member -> ids.add(member.getUserId()));
        return users.findPublic(ids);
    }

    private static StudyViews.Host host(long hostId, Map<Long, PublicUser> people) {
        PublicUser host = people.get(hostId);
        return new StudyViews.Host(hostId, host == null ? null : host.nickname(), StudyViews.HostStats.NONE_YET);
    }

    private static List<StudyViews.Member> memberViews(List<StudyMember> active, Map<Long, PublicUser> people) {
        return active.stream()
                .map(member -> {
                    PublicUser user = people.get(member.getUserId());
                    return new StudyViews.Member(member.getUserId(), user == null ? null : user.nickname(),
                            StudyViews.Trust.NEWCOMER);
                })
                .toList();
    }

    private static StudyViews.MyState myState(Study study, List<StudyMember> active, Long viewerId) {
        if (viewerId == null) {
            return StudyViews.MyState.NONE;
        }
        if (viewerId.equals(study.getHostId())) {
            return StudyViews.MyState.HOST;
        }
        // WAITING 은 대기 기능(T-017)에서 더한다.
        return active.stream().anyMatch(member -> member.getUserId().equals(viewerId))
                ? StudyViews.MyState.MEMBER
                : StudyViews.MyState.NONE;
    }
}
