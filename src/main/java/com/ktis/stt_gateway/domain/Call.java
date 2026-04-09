package com.ktis.stt_gateway.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "calls",
    indexes = {
        @Index(name = "idx_calls_start_time", columnList = "start_time"),
        @Index(name = "idx_calls_caller_number", columnList = "caller_number, start_time")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, unique = true, length = 200)
    private String callId;

    @Column(name = "caller_number", length = 50)
    private String callerNumber;

    @Column(name = "callee_number", length = 50)
    private String calleeNumber;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CallStatus status = CallStatus.RINGING;

    @Column(name = "stt_provider", length = 50)
    private String sttProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_mode", length = 20)
    private SttMode sttMode;

    @Column(name = "rx_file_path", length = 500)
    private String rxFilePath;

    @Column(name = "tx_file_path", length = 500)
    private String txFilePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void markActive() {
        this.status = CallStatus.ACTIVE;
    }

    public void markCompleted(LocalDateTime endTime) {
        this.status = CallStatus.COMPLETED;
        this.endTime = endTime;
        if (this.startTime != null) {
            this.durationSeconds = (int) Duration.between(this.startTime, endTime).getSeconds();
        }
    }

    public void markError() {
        this.status = CallStatus.ERROR;
    }

    public void setAudioPaths(String rxPath, String txPath) {
        this.rxFilePath = rxPath;
        this.txFilePath = txPath;
    }

    public void setSttInfo(String provider, SttMode mode) {
        this.sttProvider = provider;
        this.sttMode = mode;
    }
}
