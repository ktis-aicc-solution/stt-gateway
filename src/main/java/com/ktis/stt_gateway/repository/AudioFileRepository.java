package com.ktis.stt_gateway.repository;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.AudioFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    Optional<AudioFile> findByCallIdAndChannel(String callId, AudioChannel channel);

    List<AudioFile> findByCreatedAtBeforeAndIsDeletedFalse(LocalDateTime cutoff);
}
