package com.ktis.stt_gateway.domain;

import com.ktis.stt_gateway.audio.AudioChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audio_files",
    indexes = {
        @Index(name = "idx_audio_files_call_id", columnList = "call_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 200)
    private String callId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AudioChannel channel;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "WAV";

    @Column(name = "sample_rate", nullable = false)
    @Builder.Default
    private Integer sampleRate = 8000;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void markDeleted() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public void updateDuration(Long durationMs) {
        this.durationMs = durationMs;
    }
}
