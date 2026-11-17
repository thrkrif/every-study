package com.everystudy.study.service;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.event.StudyCreated;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.user.service.UserAccountService;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스터디 개설 (PRD 4.2, API 4장). */
@Service
public class StudyCreationService {

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final UserAccountService users;
    private final StudyProperties properties;
    private final TimeProvider time;
    private final ApplicationEventPublisher events;

    StudyCreationService(StudyRepository studies, StudyMemberRepository members, UserAccountService users,
            StudyProperties properties, TimeProvider time, ApplicationEventPublisher events) {
        this.studies = studies;
        this.members = members;
        this.users = users;
        this.properties = properties;
        this.time = time;
        this.events = events;
    }

    /**
     * 스터디를 만들고 주최자를 참여자로 넣는다. 채팅방은 {@link StudyCreated} 를 받은 {@code chat} 모듈이
     * 같은 트랜잭션에서 만든다. 어느 하나가 실패하면 전부 롤백된다.
     *
     * @return 새 스터디 ID
     * @throws com.everystudy.common.error.BusinessException 온보딩 전이면 {@code ONBOARDING_REQUIRED}
     * @throws InvalidInputException 시각·정원·입장 기준이 규칙에 맞지 않음
     */
    @Transactional
    public long create(long hostId, CreateStudyCommand command) {
        users.requireOnboarded(hostId);
        Instant now = time.now();
        List<FieldViolation> violations = StudyCreationRules.check(command, now, properties);
        if (!violations.isEmpty()) {
            throw new InvalidInputException(violations);
        }

        Study study = studies.save(Study.open(hostId, command.title(), command.description(), command.topicCode(),
                command.districtCode(), command.targetJobCodes(), command.place(), command.startAt(),
                command.durationMin(), StudyCreationRules.sessionDate(command.startAt()), command.capacity(),
                command.entryCriteria(), now));
        members.save(StudyMember.join(study.getId(), hostId, now));
        events.publishEvent(new StudyCreated(study.getId(), hostId));
        return study.getId();
    }
}
