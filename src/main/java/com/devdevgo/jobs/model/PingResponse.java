package com.devdevgo.jobs.model;

public record PingResponse(
        String timestamp,
        int status,
        String message,
        String path
) {
}
