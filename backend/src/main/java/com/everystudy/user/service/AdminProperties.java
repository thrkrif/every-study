package com.everystudy.user.service;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 운영자 지정 설정 (PLAN D-10). 목록에 있는 이메일로 소셜 로그인하면 {@code ADMIN} 이 된다. 이메일은 공급자가 확인한 값만
 * 저장되므로(PLAN D-9) 남의 이메일을 적어 운영자가 될 수 없다. 목록은 사람이 환경변수(예: {@code ADMIN_EMAILS}, 쉼표로
 * 구분)로 관리한다. 목록에서 빼도 이미 운영자가 된 회원은 자동으로 내려가지 않는다 (운영 DB 에서 직접 바꾼다).
 *
 * @param emails 운영자로 올릴 이메일. 대소문자는 구분하지 않는다
 */
@ConfigurationProperties("everystudy.admin")
public record AdminProperties(@DefaultValue List<String> emails) {

    boolean isAdminEmail(String email) {
        return email != null && emails.stream().anyMatch(admin -> admin.strip().toLowerCase(Locale.ROOT)
                .equals(email.strip().toLowerCase(Locale.ROOT)));
    }
}
