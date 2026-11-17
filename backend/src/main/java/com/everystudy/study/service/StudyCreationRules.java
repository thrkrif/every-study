package com.everystudy.study.service;

import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.time.TimeProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 현재 시각과 설정값에 따라 달라지는 개설 규칙 (PRD 4.2·4.14, PLAN D-21). DB·Spring 에 의존하지 않는다.
 *
 * <ul>
 *   <li>시작은 현재 이후이고, 시작 날짜(KST)는 오늘부터 개설 가능 일수 뒤까지다 (오늘이 11일이고 14일이면 25일까지).</li>
 *   <li>시작 시각은 분 단위(기본 10분)에 맞고 초가 없다.</li>
 *   <li>진행 시간은 최소값 이상, 분 단위에 맞고, 시작한 날(KST) 안에 끝난다. 끝나는 시각이 자정(24:00)이면 받는다.</li>
 *   <li>정원은 하한~상한, 최소 출석률은 고를 수 있는 값 중 하나 또는 없음.</li>
 * </ul>
 */
final class StudyCreationRules {

    private StudyCreationRules() {
    }

    static List<FieldViolation> check(CreateStudyCommand command, Instant now, StudyProperties properties) {
        List<FieldViolation> violations = new ArrayList<>();
        checkStart(command.startAt(), now, properties, violations);
        checkDuration(command.startAt(), command.durationMin(), properties, violations);

        if (command.capacity() < properties.capacityMin() || command.capacity() > properties.capacityMax()) {
            violations.add(new FieldViolation("capacity",
                    "정원은 " + properties.capacityMin() + "~" + properties.capacityMax() + "명이어야 합니다"));
        }
        Integer rate = command.entryCriteria().minAttendanceRate();
        if (rate != null && !properties.minAttendanceRateOptions().contains(rate)) {
            violations.add(new FieldViolation("minAttendanceRate",
                    "최소 출석률은 " + properties.minAttendanceRateOptions() + " 중 하나여야 합니다"));
        }
        return violations;
    }

    /** 시작 시각의 KST 날짜. 종료 처리와 목록 조회의 기준이다 (DATA-MODEL 4장 {@code session_date}). */
    static LocalDate sessionDate(Instant startAt) {
        return LocalDate.ofInstant(startAt, TimeProvider.KOREA);
    }

    private static void checkStart(Instant startAt, Instant now, StudyProperties properties,
            List<FieldViolation> violations) {
        ZonedDateTime start = startAt.atZone(TimeProvider.KOREA);
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % properties.startMinuteUnit() != 0) {
            violations.add(new FieldViolation("startAt",
                    "시작 시각은 " + properties.startMinuteUnit() + "분 단위여야 합니다"));
        }
        if (!startAt.isAfter(now)) {
            violations.add(new FieldViolation("startAt", "시작 시각은 현재 이후여야 합니다"));
            return;
        }
        LocalDate lastOpenableDay = LocalDate.ofInstant(now, TimeProvider.KOREA).plusDays(properties.openableDays());
        if (sessionDate(startAt).isAfter(lastOpenableDay)) {
            violations.add(new FieldViolation("startAt",
                    "오늘부터 " + properties.openableDays() + "일 뒤까지만 만들 수 있습니다"));
        }
    }

    private static void checkDuration(Instant startAt, int durationMin, StudyProperties properties,
            List<FieldViolation> violations) {
        if (durationMin < properties.durationMinMinutes() || durationMin % properties.durationMinuteUnit() != 0) {
            violations.add(new FieldViolation("durationMin",
                    "진행 시간은 " + properties.durationMinMinutes() + "분 이상, "
                            + properties.durationMinuteUnit() + "분 단위여야 합니다"));
            return;
        }
        Instant nextMidnight = sessionDate(startAt).plusDays(1).atStartOfDay(TimeProvider.KOREA).toInstant();
        if (startAt.plusSeconds(durationMin * 60L).isAfter(nextMidnight)) {
            violations.add(new FieldViolation("durationMin", "시작한 날 안에 끝나야 합니다"));
        }
    }
}
