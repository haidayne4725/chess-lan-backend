package com.chesslan.game.infrastructure.chess;

public record ChessMoveResult(
        boolean accepted,
        String errorCode,
        String message,
        String notation,
        String fen,
        String turn,
        boolean check,
        boolean checkmate,
        boolean draw,
        String drawReason
) {
}
