package com.ktis.stt_gateway.repository;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.SttResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SttResultRepository extends JpaRepository<SttResultEntity, Long> {

    List<SttResultEntity> findByCallIdOrderByStartOffsetMsAsc(String callId);

    List<SttResultEntity> findByCallIdAndChannelAndIsFinalTrueOrderByStartOffsetMsAsc(
        String callId, AudioChannel channel);
}
