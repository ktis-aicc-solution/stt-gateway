package com.ktis.stt_gateway.sip;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SipMessage {
    private final String callId;
    private final String fromNumber;
    private final String toNumber;
    private final SipMethod method;
    private final String customerIp;
    private final int customerRtpPort;
    private final int agentRtpPort;
}
