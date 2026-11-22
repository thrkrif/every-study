package com.everystudy.trust.repository;

import com.everystudy.trust.domain.HostPenaltyEvent;
import com.everystudy.trust.domain.HostPenaltyReason;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostPenaltyEventRepository extends JpaRepository<HostPenaltyEvent, Long> {

    boolean existsByStudyIdAndHostIdAndReason(Long studyId, Long hostId, HostPenaltyReason reason);

    /** 사유별 건수를 주최자마다 센다. 건이 없는 주최자는 결과에 없다. */
    @Query("select new com.everystudy.trust.repository.HostPenaltyCount(e.hostId, count(e)) "
            + "from HostPenaltyEvent e where e.hostId in :hostIds and e.reason = :reason group by e.hostId")
    List<HostPenaltyCount> countByHostIdsAndReason(@Param("hostIds") Collection<Long> hostIds,
            @Param("reason") HostPenaltyReason reason);
}
