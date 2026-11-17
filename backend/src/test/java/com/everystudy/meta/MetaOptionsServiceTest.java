package com.everystudy.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.attendance.service.AttendanceProperties;
import com.everystudy.meta.service.MetaOptions;
import com.everystudy.meta.service.MetaOptionsService;
import com.everystudy.study.service.StudyProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetaOptionsServiceTest {

    private static final StudyProperties DEFAULT_STUDY =
            new StudyProperties(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100), 10, 30);

    private static MetaOptions options(StudyProperties study, AttendanceProperties attendance) {
        return new MetaOptionsService(study, attendance).options();
    }

    @Test
    @DisplayName("수치는 설정 객체에서 읽는다. 설정을 바꾸면 응답이 바뀐다")
    void numbersComeFromSettings() {
        MetaOptions defaults = options(DEFAULT_STUDY, new AttendanceProperties(150));
        MetaOptions changed = options(
                new StudyProperties(3, 8, 7, 30, 15, 60, List.of(80, 90), 10, 30), new AttendanceProperties(200));

        assertThat(defaults.study())
                .isEqualTo(new MetaOptions.Study(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100)));
        assertThat(defaults.checkin()).isEqualTo(new MetaOptions.Checkin(150));
        assertThat(changed.study()).isEqualTo(new MetaOptions.Study(3, 8, 7, 30, 15, 60, List.of(80, 90)));
        assertThat(changed.checkin()).isEqualTo(new MetaOptions.Checkin(200));
    }

    @Test
    @DisplayName("직군은 대분류 아래 세부 직무를, 지역은 시·도 → 시 → 구를 children 으로 담는다")
    void codeTrees() {
        MetaOptions options = options(DEFAULT_STUDY, new AttendanceProperties(150));

        MetaOptions.Option it = options.jobs().getFirst();
        assertThat(it.code()).isEqualTo("IT");
        assertThat(it.children()).extracting(MetaOptions.Option::code).startsWith("IT_BACKEND", "IT_FRONTEND");
        assertThat(options.jobs()).extracting(MetaOptions.Option::code).doesNotContain("IT_BACKEND");

        MetaOptions.Option suwon = options.regions().getFirst().children().getFirst();
        assertThat(suwon.name()).isEqualTo("수원시");
        assertThat(suwon.children()).extracting(MetaOptions.Option::name)
                .containsExactly("장안구", "권선구", "팔달구", "영통구");
        assertThat(options.topics()).extracting(MetaOptions.Option::code)
                .containsExactly("JOB_PREP", "CERTIFICATE", "LANGUAGE", "CIVIL_SERVICE", "DEVELOPMENT", "ETC");
    }
}
