package com.ktis.stt_gateway.exception;

public class CallNotFoundException extends SttGatewayException {
    public CallNotFoundException(String callId) {
        super("콜을 찾을 수 없습니다: " + callId);
    }
}
