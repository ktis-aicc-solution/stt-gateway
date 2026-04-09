package com.ktis.stt_gateway.exception;

public class SttGatewayException extends RuntimeException {
    public SttGatewayException(String message) { super(message); }
    public SttGatewayException(String message, Throwable cause) { super(message, cause); }
}
