package com.everystudy.meta.service;

import com.everystudy.attendance.service.AttendanceProperties;
import com.everystudy.common.code.JobCode;
import com.everystudy.common.code.RegionCode;
import com.everystudy.common.code.TopicCode;
import com.everystudy.study.service.StudyProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@link MetaOptions} 를 만든다. 코드 목록은 enum, 수치는 각 모듈의 설정 객체에서 읽는다.
 * 그래서 설정을 바꾸면 이 응답도 바뀌고, 클라이언트는 숫자를 따로 갖지 않는다 (ARCHITECTURE 4.9).
 */
@Service
public class MetaOptionsService {

    private final StudyProperties study;
    private final AttendanceProperties attendance;

    public MetaOptionsService(StudyProperties study, AttendanceProperties attendance) {
        this.study = study;
        this.attendance = attendance;
    }

    public MetaOptions options() {
        return new MetaOptions(
                Arrays.stream(TopicCode.values()).map(t -> new MetaOptions.Option(t.name(), t.label(), List.of())).toList(),
                JobCode.categories().stream().map(MetaOptionsService::job).toList(),
                RegionCode.provinces().stream().map(MetaOptionsService::region).toList(),
                new MetaOptions.Study(study.capacityMin(), study.capacityMax(), study.openableDays(),
                        study.startMinuteUnit(), study.durationMinuteUnit(), study.durationMinMinutes(),
                        List.copyOf(study.minAttendanceRateOptions())),
                new MetaOptions.Checkin(attendance.checkinRadiusMeters()));
    }

    private static MetaOptions.Option job(JobCode job) {
        return new MetaOptions.Option(job.name(), job.label(), job.details().stream().map(MetaOptionsService::job).toList());
    }

    private static MetaOptions.Option region(RegionCode region) {
        return new MetaOptions.Option(region.name(), region.label(),
                region.children().stream().map(MetaOptionsService::region).toList());
    }
}
