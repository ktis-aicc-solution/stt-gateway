package com.ktis.stt_gateway.repository;

import com.ktis.stt_gateway.domain.Call;
import com.ktis.stt_gateway.domain.CallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CallRepository extends JpaRepository<Call, Long> {

    Optional<Call> findByCallId(String callId);

    List<Call> findByStatusInOrderByStartTimeDesc(List<CallStatus> statuses);

    @Query("SELECT c FROM Call c WHERE c.startTime >= :from AND c.startTime < :to ORDER BY c.startTime DESC")
    Page<Call> findByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Modifying
    @Query("UPDATE Call c SET c.status = :status, c.endTime = :endTime, " +
           "c.durationSeconds = :durationSeconds, c.updatedAt = :updatedAt WHERE c.callId = :callId")
    void updateCallEnd(@Param("callId") String callId,
                       @Param("endTime") LocalDateTime endTime,
                       @Param("durationSeconds") Integer durationSeconds,
                       @Param("status") CallStatus status,
                       @Param("updatedAt") LocalDateTime updatedAt);
}
