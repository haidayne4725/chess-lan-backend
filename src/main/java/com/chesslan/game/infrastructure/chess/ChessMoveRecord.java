package com.chesslan.game.infrastructure.chess;

public record ChessMoveRecord(
        String from,
        String to,
        String promotion
) {
}
