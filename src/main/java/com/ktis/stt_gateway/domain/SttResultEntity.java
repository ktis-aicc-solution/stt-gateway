package com.ktis.stt_gateway.domain;

import com.ktis.stt_gateway.audio.AudioChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stt_results",
    indexes = {
        @Index(name = "idx_stt_results_call_id", columnList = "call_id, channel, created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SttResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 200)
    private String callId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AudioChannel channel;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column
    private Float confidence;

    @Column(name = "start_offset_ms")
    private Long startOffsetMs;

    @Column(name = "end_offset_ms")
    private Long endOffsetMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_mode", nullable = false, length = 20)
    private SttMode sttMode;

    @Column(name = "is_final", nullable = false)
    @Builder.Default
    private Boolean isFinal = false;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
