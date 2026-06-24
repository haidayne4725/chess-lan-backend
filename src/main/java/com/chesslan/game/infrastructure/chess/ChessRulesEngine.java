package com.chesslan.game.infrastructure.chess;

import java.util.List;

public interface ChessRulesEngine {
    String initialFen();
    ChessMoveResult applyMove(List<ChessMoveRecord> previousMoves, ChessMoveCommand command);
}
