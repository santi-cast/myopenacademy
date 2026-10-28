package dev.codeacademy.api;

/** The single error shape of the API: a stable code and a fixed message, never request data. */
public record ApiError(String code, String message) {
}
