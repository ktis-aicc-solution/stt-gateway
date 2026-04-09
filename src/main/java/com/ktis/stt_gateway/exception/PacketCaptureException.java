package com.ktis.stt_gateway.exception;

public class PacketCaptureException extends SttGatewayException {
    public PacketCaptureException(String message) { super(message); }
    public PacketCaptureException(String message, Throwable cause) { super(message, cause); }
}
