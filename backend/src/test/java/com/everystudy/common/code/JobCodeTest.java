package com.everystudy.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobCodeTest {

    @Test
    @DisplayName("세부 직무는 자기 대분류를 알고, 대분류의 대분류는 자기 자신이다")
    void detailKnowsCategory() {
        assertThat(JobCode.IT_BACKEND.category()).isEqualTo(JobCode.IT);
        assertThat(JobCode.IT.category()).isEqualTo(JobCode.IT);
        assertThat(JobCode.IT_BACKEND.isCategory()).isFalse();
    }

    @Test
    @DisplayName("대분류의 세부 직무는 선언 순서대로이고, 세부 직무가 없는 대분류도 있다")
    void detailsOfCategory() {
        assertThat(JobCode.PUBLIC.details()).containsExactly(
                JobCode.PUBLIC_ADMIN, JobCode.PUBLIC_POLICE_FIRE, JobCode.PUBLIC_TEACHER, JobCode.PUBLIC_ENTERPRISE);
        assertThat(JobCode.DESIGN.details()).isEmpty();
        assertThat(JobCode.IT_BACKEND.details()).isEmpty();
    }

    @Test
    @DisplayName("모든 세부 직무는 대분류 아래에 있고, 코드는 DB 컬럼 길이(30자) 안이다")
    void everyCodeFitsColumnAndHasCategory() {
        assertThat(JobCode.values()).allSatisfy(job -> {
            assertThat(job.name()).hasSizeLessThanOrEqualTo(30);
            assertThat(job.category().isCategory()).isTrue();
        });
        assertThat(JobCode.categories()).first().isEqualTo(JobCode.IT);
        assertThat(JobCode.categories()).last().isEqualTo(JobCode.ETC);
    }

    @Test
    @DisplayName("코드 문자열로 찾고, 모르는 값은 빈 값이다 (대소문자 구분)")
    void find() {
        assertThat(JobCode.find("IT_BACKEND")).contains(JobCode.IT_BACKEND);
        assertThat(JobCode.find("it_backend")).isEmpty();
        assertThat(JobCode.find("ANY")).isEmpty();
        assertThat(JobCode.find(null)).isEmpty();
    }
}
