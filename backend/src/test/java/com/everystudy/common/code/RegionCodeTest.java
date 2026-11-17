package com.everystudy.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.code.RegionCode.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionCodeTest {

    @Test
    @DisplayName("시·도 → 시 → 구의 3단계다")
    void levels() {
        assertThat(RegionCode.GYEONGGI.level()).isEqualTo(Level.PROVINCE);
        assertThat(RegionCode.SUWON.level()).isEqualTo(Level.CITY);
        assertThat(RegionCode.SUWON_YEONGTONG.level()).isEqualTo(Level.DISTRICT);
        assertThat(RegionCode.SUWON_YEONGTONG.parent()).isEqualTo(RegionCode.SUWON);
    }

    @Test
    @DisplayName("수원시 4개 구로 시작한다 (PRD 1장)")
    void startsWithFourSuwonDistricts() {
        assertThat(RegionCode.provinces()).containsExactly(RegionCode.GYEONGGI);
        assertThat(RegionCode.GYEONGGI.children()).containsExactly(RegionCode.SUWON);
        assertThat(RegionCode.SUWON.children()).containsExactly(
                RegionCode.SUWON_JANGAN, RegionCode.SUWON_GWONSEON, RegionCode.SUWON_PALDAL, RegionCode.SUWON_YEONGTONG);
    }

    @Test
    @DisplayName("코드는 DB 컬럼 길이(20자) 안이다")
    void everyCodeFitsColumn() {
        assertThat(RegionCode.values()).allSatisfy(region -> assertThat(region.name()).hasSizeLessThanOrEqualTo(20));
    }

    @Test
    @DisplayName("어느 단계든 그 아래의 구 전부로 펼친다. 구는 자기 자신이다")
    void districts() {
        assertThat(RegionCode.GYEONGGI.districts()).hasSize(4);
        assertThat(RegionCode.SUWON.districts()).containsExactly(
                RegionCode.SUWON_JANGAN, RegionCode.SUWON_GWONSEON, RegionCode.SUWON_PALDAL, RegionCode.SUWON_YEONGTONG);
        assertThat(RegionCode.SUWON_PALDAL.districts()).containsExactly(RegionCode.SUWON_PALDAL);
    }
}
