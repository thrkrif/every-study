package com.everystudy.trust.repository;

import com.everystudy.trust.domain.SanctionKind;
import com.everystudy.trust.domain.UserSanction;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    /** 지금 유효한 제재 중 가장 늦게 끝나는 시각. 유효한 제재가 없으면 빈 값이다. */
    @Query("""
            select max(s.endsAt) from UserSanction s
            where s.userId = :userId and s.kind in :kinds
              and s.startsAt <= :now and s.endsAt > :now and s.revokedAt is null
            """)
    Optional<Instant> findLatestActiveEnd(@Param("userId") long userId, @Param("kinds") Collection<SanctionKind> kinds,
            @Param("now") Instant now);
}
