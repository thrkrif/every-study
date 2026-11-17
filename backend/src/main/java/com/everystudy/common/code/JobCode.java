package com.everystudy.common.code;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 직군 (DATA-MODEL 1장 고정 목록, PRD 4.1·4.2). 대분류와 그 아래 세부 직무의 2단계다.
 *
 * <ul>
 *   <li>회원의 내 직군: 대분류 하나는 필수, 세부 직무는 선택이다. 세부 직무를 고르면 그 코드 하나를 저장한다
 *       (대분류는 세부 직무가 알고 있다).</li>
 *   <li>스터디의 희망 직군: 지금은 대분류만 여러 개 고를 수 있고, 하나도 고르지 않으면 직군 무관이다.
 *       희망 직군은 참여 조건이라 내 직군의 대분류가 목록에 없으면 신청할 수 없다 (PRD 4.4).</li>
 * </ul>
 *
 * <p>값 추가는 코드 변경이다. 이미 저장된 코드는 지우거나 이름을 바꾸지 않는다.
 */
public enum JobCode {

    IT(null, "IT·개발"),
    IT_BACKEND(IT, "백엔드"),
    IT_FRONTEND(IT, "프론트엔드"),
    IT_MOBILE(IT, "모바일(Android·iOS)"),
    IT_DATA_AI(IT, "데이터·AI"),
    IT_DEVOPS(IT, "인프라·DevOps"),
    IT_QA(IT, "QA"),

    PUBLIC(null, "공무원·공공"),
    PUBLIC_ADMIN(PUBLIC, "행정직"),
    PUBLIC_POLICE_FIRE(PUBLIC, "경찰·소방"),
    PUBLIC_TEACHER(PUBLIC, "교원 임용"),
    PUBLIC_ENTERPRISE(PUBLIC, "공기업"),

    OFFICE(null, "경영·사무"),
    OFFICE_HR(OFFICE, "인사·총무"),
    OFFICE_FINANCE(OFFICE, "재무·회계"),
    OFFICE_STRATEGY(OFFICE, "기획·전략"),

    MARKETING(null, "마케팅·영업"),
    MARKETING_AD(MARKETING, "마케팅·광고"),
    MARKETING_SALES(MARKETING, "영업"),
    MARKETING_CS(MARKETING, "고객 서비스"),

    DESIGN(null, "디자인"),
    FINANCE(null, "금융"),
    ENGINEERING(null, "생산·엔지니어링"),
    RESEARCH(null, "연구개발"),
    MEDICAL(null, "의료·보건"),
    EDUCATION(null, "교육"),
    ETC(null, "기타");

    private final JobCode category;
    private final String label;

    JobCode(JobCode category, String label) {
        this.category = category;
        this.label = label;
    }

    public static Optional<JobCode> find(String code) {
        return Arrays.stream(values()).filter(job -> job.name().equals(code)).findFirst();
    }

    public static List<JobCode> categories() {
        return Arrays.stream(values()).filter(JobCode::isCategory).toList();
    }

    public boolean isCategory() {
        return category == null;
    }

    /** 대분류면 자기 자신, 세부 직무면 속한 대분류. */
    public JobCode category() {
        return isCategory() ? this : category;
    }

    /** 이 대분류의 세부 직무. 세부 직무에서 부르면 비어 있다. */
    public List<JobCode> details() {
        return Arrays.stream(values()).filter(job -> job.category == this).toList();
    }

    public String label() {
        return label;
    }
}
