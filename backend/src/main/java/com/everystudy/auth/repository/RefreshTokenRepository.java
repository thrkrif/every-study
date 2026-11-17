package com.everystudy.auth.repository;

import com.everystudy.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 행을 잠그고 읽는다. 같은 토큰으로 갱신이 동시에 두 번 오면 한 건씩 처리돼,
     * 뒤의 요청은 앞의 요청이 쓴 것으로 표시한 토큰을 보게 된다 (재사용 탐지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 계열의 아직 폐기되지 않은 토큰을 모두 폐기한다. 폐기한 개수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);
}
