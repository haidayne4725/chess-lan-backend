package com.chesslan.game.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(4000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(4001, "Username or password is incorrect", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(4002, "Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    USERNAME_EXISTS(4003, "Username already exists", HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND(4004, "Resource not found", HttpStatus.NOT_FOUND),
    ROOM_NOT_JOINABLE(4005, "Room cannot be joined", HttpStatus.CONFLICT),
    FORBIDDEN(4006, "You cannot perform this action", HttpStatus.FORBIDDEN),
    MATCH_NOT_ACTIVE(4007, "Match is not active", HttpStatus.CONFLICT),
    NOT_YOUR_TURN(4008, "It is not your turn", HttpStatus.CONFLICT),
    ILLEGAL_MOVE(4009, "Move is not legal", HttpStatus.UNPROCESSABLE_CONTENT),
    MATCH_ALREADY_STARTED(4010, "Match has already started", HttpStatus.CONFLICT),
    INTERNAL_ERROR(5000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus status;
}
