package com.chesslan.game.infrastructure.chess;

public record ChessMoveCommand(
        String from,
        String to,
        String promotion
) {
}
