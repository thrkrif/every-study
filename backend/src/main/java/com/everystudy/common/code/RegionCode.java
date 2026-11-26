package com.everystudy.common.code;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지역 (DATA-MODEL 1장 고정 목록, PRD 4.1·4.3). 전국의 시·도 한 단계다.
 *
 * <ul>
 *   <li>회원의 활동 지역과 스터디의 지역은 이 코드 하나로 저장한다.</li>
 *   <li>대전과 세종은 한 지역으로 묶는다. 생활권이 겹치고 세종만으로는 스터디가 적어서다.</li>
 * </ul>
 *
 * <p>선언 순서가 화면에 보일 순서다.
 */
public enum RegionCode {

    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    GANGWON("강원"),
    DAEJEON_SEJONG("대전/세종"),
    CHUNGNAM("충남"),
    CHUNGBUK("충북"),
    DAEGU("대구"),
    GYEONGBUK("경북"),
    BUSAN("부산"),
    ULSAN("울산"),
    GYEONGNAM("경남"),
    GWANGJU("광주"),
    JEONNAM("전남"),
    JEONBUK("전북"),
    JEJU("제주");

    private final String label;

    RegionCode(String label) {
        this.label = label;
    }

    public static Optional<RegionCode> find(String code) {
        return Arrays.stream(values()).filter(region -> region.name().equals(code)).findFirst();
    }

    public String label() {
        return label;
    }
}
