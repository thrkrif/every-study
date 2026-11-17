package com.everystudy.user.repository;

import com.everystudy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 다른 회원이 이 닉네임을 쓰고 있는가. 비교는 DB 정렬 규칙을 따라 영문 대소문자를 구분하지 않는다. */
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
