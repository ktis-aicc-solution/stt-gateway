package com.ktis.stt_gateway.domain;

public enum CallStatus {
    RINGING,    // 연결 중
    ACTIVE,     // 통화 중
    COMPLETED,  // 정상 종료
    ERROR       // 오류
}
