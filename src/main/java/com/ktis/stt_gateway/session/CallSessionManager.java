package com.ktis.stt_gateway.session;

import com.ktis.stt_gateway.audio.AudioFileWriter;
import com.ktis.stt_gateway.config.AppProperties;
import com.ktis.stt_gateway.domain.Call;
import com.ktis.stt_gateway.domain.CallStatus;
import com.ktis.stt_gateway.repository.CallRepository;
import com.ktis.stt_gateway.sip.SipMessage;
import com.ktis.stt_gateway.stt.SttStreamingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CallSessionManager {

    private final ConcurrentHashMap<String, CallSession> activeSessions = new ConcurrentHashMap<>();
    private final CallRepository callRepository;
    private final AudioFileWriter audioFileWriter;
    private final SttStreamingManager sttStreamingManager;
    private final AppProperties props;

    @Transactional
    public void onCallStart(SipMessage sip, Timestamp timestamp) {
        String callId = sip.getCallId();
        if (activeSessions.containsKey(callId)) return;

        if (!isAllowedCall(sip)) {
            log.debug("통화 필터링: callId={}, from={}, to={}", callId,
                maskPhoneNumber(sip.getFromNumber()), maskPhoneNumber(sip.getToNumber()));
            return;
        }

        LocalDateTime startTime = timestamp != null
            ? timestamp.toLocalDateTime()
            : LocalDateTime.now();

        CallSession session = CallSession.builder()
            .callId(callId)
            .callerNumber(sip.getFromNumber())
            .calleeNumber(sip.getToNumber())
            .startTime(startTime)
            .status(CallStatus.RINGING)
            .customerIp(sip.getCustomerIp())
            .customerRtpPort(sip.getCustomerRtpPort())
            .build();

        activeSessions.put(callId, session);

        Call call = Call.builder()
            .callId(callId)
            .callerNumber(sip.getFromNumber())
            .calleeNumber(sip.getToNumber())
            .startTime(startTime)
            .status(CallStatus.RINGING)
            .build();
        callRepository.save(call);

        log.info("콜 시작: callId={}, from={}", callId, maskPhoneNumber(sip.getFromNumber()));
    }

    @Transactional
    public void onCallConnected(SipMessage sip, Timestamp timestamp) {
        CallSession session = activeSessions.get(sip.getCallId());
        if (session == null) return;

        session.setStatus(CallStatus.ACTIVE);
        callRepository.findByCallId(sip.getCallId())
            .ifPresent(Call::markActive);

        log.debug("콜 연결됨: callId={}", sip.getCallId());
    }

    @Async("callProcessingExecutor")
    @Transactional
    public void onCallEnd(SipMessage sip, Timestamp timestamp) {
        CallSession session = activeSessions.remove(sip.getCallId());
        if (session == null) return;

        LocalDateTime endTime = timestamp != null
            ? timestamp.toLocalDateTime()
            : LocalDateTime.now();
        session.setEndTime(endTime);
        session.setStatus(CallStatus.COMPLETED);

        callRepository.findByCallId(sip.getCallId())
            .ifPresent(call -> call.markCompleted(endTime));

        log.info("콜 종료: callId={}, duration={}초",
            session.getCallId(), session.getDurationSeconds());

        sttStreamingManager.stopSession(session.getCallId()); // 스트리밍 STT 종료
        audioFileWriter.finalizeAndSave(session);             // 아카이브용 WAV 저장
    }

    public CallSession findByCallId(String callId) {
        return activeSessions.get(callId);
    }

    public CallSession findByRtpPort(int dstPort, String srcIp) {
        return activeSessions.values().stream()
            .filter(s -> s.getCustomerRtpPort() == dstPort
                || s.getAgentRtpPort() == dstPort
                || s.isCustomerIp(srcIp))
            .findFirst()
            .orElse(null);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    private boolean isAllowedCall(SipMessage sip) {
        var callerPrefixes = props.getSip().getCallerNumberPrefixes();
        var calleePrefixes = props.getSip().getCalleeNumberPrefixes();

        if (callerPrefixes.isEmpty() && calleePrefixes.isEmpty()) return true;

        String caller = sip.getFromNumber();
        String callee = sip.getToNumber();

        if (!callerPrefixes.isEmpty() && caller != null) {
            if (callerPrefixes.stream().anyMatch(caller::startsWith)) return true;
        }
        if (!calleePrefixes.isEmpty() && callee != null) {
            if (calleePrefixes.stream().anyMatch(callee::startsWith)) return true;
        }
        return false;
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) return "****";
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(8);
    }
}
