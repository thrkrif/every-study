package com.everystudy.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionCodeTest {

    @Test
    @DisplayName("전국 시·도 16개를 한 단계로 둔다. 대전과 세종은 하나로 묶는다")
    void provincesOfKorea() {
        assertThat(Arrays.stream(RegionCode.values()).map(RegionCode::label)).containsExactly(
                "서울", "경기", "인천", "강원", "대전/세종", "충남", "충북", "대구",
                "경북", "부산", "울산", "경남", "광주", "전남", "전북", "제주");
    }

    @Test
    @DisplayName("코드는 DB 컬럼 길이(20자) 안이다")
    void everyCodeFitsColumn() {
        assertThat(RegionCode.values()).allSatisfy(region -> assertThat(region.name()).hasSizeLessThanOrEqualTo(20));
    }

    @Test
    @DisplayName("코드 문자열로 찾는다. 없는 코드는 비어 있다")
    void find() {
        assertThat(RegionCode.find("DAEJEON_SEJONG")).contains(RegionCode.DAEJEON_SEJONG);
        assertThat(RegionCode.find("SUWON")).isEmpty();
    }
}
