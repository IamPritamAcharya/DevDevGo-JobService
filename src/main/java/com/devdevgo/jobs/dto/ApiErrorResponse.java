package com.devdevgo.jobs.dto;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path) {
}