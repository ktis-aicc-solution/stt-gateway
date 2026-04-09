package com.ktis.stt_gateway.rtp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RtpPacket {
    private final int payloadType;
    private final int seqNumber;
    private final long ssrc;
    private final byte[] payload;
}
