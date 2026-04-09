package com.ktis.stt_gateway.session;

import com.ktis.stt_gateway.audio.AudioChannel;
import com.ktis.stt_gateway.domain.CallStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
@Builder
public class CallSession {

    private final String callId;
    private final String callerNumber;
    private final String calleeNumber;
    private final LocalDateTime startTime;

    @Setter
    private LocalDateTime endTime;

    @Setter
    private CallStatus status;

    @Setter
    private String customerIp;

    @Setter
    private int customerRtpPort;

    @Setter
    private int agentRtpPort;

    @Builder.Default
    private final Queue<short[]> rxBuffer = new ConcurrentLinkedQueue<>();

    @Builder.Default
    private final Queue<short[]> txBuffer = new ConcurrentLinkedQueue<>();

    public void appendAudio(AudioChannel channel, short[] samples) {
        if (channel == AudioChannel.RX) {
            rxBuffer.add(samples);
        } else {
            txBuffer.add(samples);
        }
    }

    public boolean isCustomerIp(String ip) {
        return customerIp != null && customerIp.equals(ip);
    }

    public boolean isActive() {
        return status == CallStatus.ACTIVE || status == CallStatus.RINGING;
    }

    public long getDurationSeconds() {
        if (startTime == null || endTime == null) return 0;
        return Duration.between(startTime, endTime).getSeconds();
    }
}
