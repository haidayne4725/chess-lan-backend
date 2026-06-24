package com.chesslan.game;

import com.chesslan.game.infrastructure.chess.ChessLibRulesEngine;
import com.chesslan.game.infrastructure.chess.ChessMoveCommand;
import com.chesslan.game.infrastructure.chess.ChessMoveRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChessRulesEngineTest {
    private final ChessLibRulesEngine engine = new ChessLibRulesEngine();

    @Test
    void rejectsIllegalMove() {
        var result = engine.applyMove(List.of(), new ChessMoveCommand("e2", "e5", null));

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ILLEGAL_MOVE");
    }

    @Test
    void detectsFoolMate() {
        var previous = List.of(
                new ChessMoveRecord("f2", "f3", null),
                new ChessMoveRecord("e7", "e5", null),
                new ChessMoveRecord("g2", "g4", null)
        );

        var result = engine.applyMove(previous, new ChessMoveCommand("d8", "h4", null));

        assertThat(result.accepted()).isTrue();
        assertThat(result.checkmate()).isTrue();
        assertThat(result.notation()).contains("#");
    }
}
