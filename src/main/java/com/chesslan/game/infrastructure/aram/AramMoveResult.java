package com.chesslan.game.infrastructure.aram;

public record AramMoveResult(
        boolean accepted,
        String errorCode,
        String message,
        String notation,
        String fen,
        String turn,
        boolean check,
        boolean checkmate,
        boolean draw,
        String drawReason,
        AramMatchState aramState
) {
    public static AramMoveResult rejected(String code, String message) {
        return new AramMoveResult(false, code, message, null, null, null,
                false, false, false, null, null);
    }
}
