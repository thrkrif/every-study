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

    /** 자동으로 건 제재 중 해제되지 않은 것의 가장 늦은 시작 시각. 없으면 빈 값이다. */
    @Query("""
            select max(s.startsAt) from UserSanction s
            where s.userId = :userId and s.kind = :kind and s.source = com.everystudy.trust.domain.SanctionSource.AUTO
              and s.revokedAt is null
            """)
    Optional<Instant> findLatestAutoStart(@Param("userId") long userId, @Param("kind") SanctionKind kind);

    /** 지금 유효한 자동 제재 중 주어진 종류의 것. */
    @Query("""
            select s from UserSanction s
            where s.userId = :userId and s.kind in :kinds and s.source = com.everystudy.trust.domain.SanctionSource.AUTO
              and s.revokedAt is null and s.endsAt > :now
            """)
    java.util.List<UserSanction> findActiveAuto(@Param("userId") long userId,
            @Param("kinds") Collection<SanctionKind> kinds, @Param("now") Instant now);

    /**
     * 운영자 조회: 최근 것부터. {@code userId} 가 0 이면 모든 회원, {@code activeOnly} 면 지금 유효한 것만,
     * {@code reviewOnly} 면 운영자 검토 대상만.
     */
    @Query("""
            select s from UserSanction s
            where s.id < :beforeId and (:userId = 0 or s.userId = :userId)
              and (:activeOnly = false or (s.revokedAt is null and s.endsAt > :now))
              and (:reviewOnly = false or s.reviewRequired = true)
            order by s.id desc
            """)
    java.util.List<UserSanction> findForAdmin(@Param("beforeId") long beforeId, @Param("userId") long userId,
            @Param("activeOnly") boolean activeOnly, @Param("reviewOnly") boolean reviewOnly,
            @Param("now") Instant now, org.springframework.data.domain.Limit limit);
}
