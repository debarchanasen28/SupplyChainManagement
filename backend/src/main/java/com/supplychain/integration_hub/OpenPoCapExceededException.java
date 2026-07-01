package com.supplychain.integration_hub;

/** Thrown when the number of concurrently-open inbound POs hits the configured cap. */
public class OpenPoCapExceededException extends RuntimeException {
    public OpenPoCapExceededException(String message) {
        super(message);
    }
}
