package com.everystudy.common.code;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 지역 (DATA-MODEL 1장 고정 목록, PRD 4.1·4.3). 시·도 → 시 → 구의 3단계다.
 *
 * <ul>
 *   <li>회원의 활동 지역과 스터디의 지역은 구({@link Level#DISTRICT})로 저장한다.</li>
 *   <li>탐색은 어느 단계로도 거를 수 있다. "내 지역"은 회원의 구가 속한 시다 (PRD 4.3).</li>
 * </ul>
 *
 * <p>서비스는 수원시 4개 구로 시작한다 (PRD 1장). 지역을 넓히면 여기에 추가한다.
 */
public enum RegionCode {

    GYEONGGI(null, "경기"),
    SUWON(GYEONGGI, "수원시"),
    SUWON_JANGAN(SUWON, "장안구"),
    SUWON_GWONSEON(SUWON, "권선구"),
    SUWON_PALDAL(SUWON, "팔달구"),
    SUWON_YEONGTONG(SUWON, "영통구");

    public enum Level { PROVINCE, CITY, DISTRICT }

    private final RegionCode parent;
    private final String label;

    RegionCode(RegionCode parent, String label) {
        this.parent = parent;
        this.label = label;
    }

    public static Optional<RegionCode> find(String code) {
        return Arrays.stream(values()).filter(region -> region.name().equals(code)).findFirst();
    }

    public static List<RegionCode> provinces() {
        return Arrays.stream(values()).filter(region -> region.parent == null).toList();
    }

    public Level level() {
        if (parent == null) {
            return Level.PROVINCE;
        }
        return parent.parent == null ? Level.CITY : Level.DISTRICT;
    }

    /** 바로 아래 단계의 지역. */
    public List<RegionCode> children() {
        return Arrays.stream(values()).filter(region -> region.parent == this).toList();
    }

    public RegionCode parent() {
        return parent;
    }

    public String label() {
        return label;
    }
}
