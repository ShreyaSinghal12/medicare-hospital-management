package com.medicare.dto.common;

/** Simple acknowledgement body, e.g. {"message": "Password updated"}. */
public record MessageResponse(String message) {
}
