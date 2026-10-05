package com.chesslan.game;

import com.chesslan.game.infrastructure.chess.*;
import com.chesslan.game.model.entity.*;
import com.chesslan.game.service.impl.MatchStatisticsProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class MatchStatisticsProjectorTest {
    private final ChessLibRulesEngine engine = new ChessLibRulesEngine();
    private final MatchStatisticsProjector projector = new MatchStatisticsProjector();
    private final MatchEntity match = new MatchEntity();
    private final List<MatchMoveEntity> moves = new ArrayList<>();
    private final List<ChessMoveRecord> history = new ArrayList<>();

    MatchStatisticsProjectorTest() {
        var white = new UserEntity(); white.setId(UUID.randomUUID());
        var black = new UserEntity(); black.setId(UUID.randomUUID());
        match.setWhitePlayer(white); match.setBlackPlayer(black);
        match.setStartedAt(LocalDateTime.now().minusSeconds(20)); match.setCurrentFen(engine.initialFen());
    }
    private void play(String... commands) {
        for (String command : commands) {
            String from=command.substring(0,2),to=command.substring(2,4),promotion=command.length()>4?command.substring(4):null;
            var result=engine.applyMove(history,new ChessMoveCommand(from,to,promotion));
            assertThat(result.accepted()).as(command).isTrue();
            var move=new MatchMoveEntity(); move.setId(UUID.randomUUID()); move.setMoveNumber(moves.size()+1);
            move.setPlayer(moves.size()%2==0?match.getWhitePlayer():match.getBlackPlayer());
            move.setFromSquare(from); move.setToSquare(to); move.setPromotion(promotion);
            move.setFenAfter(result.fen()); move.setNotation(result.notation());
            moves.add(move); history.add(new ChessMoveRecord(from,to,promotion));
            match.setMoveCount(moves.size()); match.setCurrentFen(result.fen());
        }
    }
    @Test void enPassantCountsOnePawnAndNoRemovalEvent() {
        play("e2e4","a7a6","e4e5","d7d5","e5d6");
        var stats=projector.project(match,moves);
        assertThat(stats.capturesComplete()).isTrue();
        assertThat(stats.entries()).hasSize(5);
        assertThat(stats.entries().get(4).captured()).isEqualTo("Pawn");
    }
    @Test void promotionCountsActualVictimsWithoutInventingLostPawns() {
        play("a2a4","h7h5","a4a5","h5h4","a5a6","h4h3","a6b7","h3g2","b7a8Q");
        var stats=projector.project(match,moves);
        assertThat(stats.capturesComplete()).isTrue();
        assertThat(stats.entries()).hasSize(9);
        assertThat(stats.entries().get(8).captured()).isEqualTo("Rook");
        assertThat(stats.entries().stream().filter(e->e.captured()!=null).map(e->e.captured())).containsExactly("Pawn","Pawn","Rook");
    }
    @Test void castlingDoesNotInventBoardEffects() {
        play("e2e4","e7e5","g1f3","b8c6","f1c4","g8f6","e1g1");
        var stats=projector.project(match,moves);
        assertThat(stats.capturesComplete()).isTrue(); assertThat(stats.entries()).hasSize(7);
        assertThat(stats.entries()).allMatch(e->e.isMove()&&e.captured()==null);
    }
    @Test void publicDestructionIsSeparateFromTheDirectCapture() {
        play("e2e4","d7d5","e4d5");
        var board=new Board(); board.loadFromFen(match.getCurrentFen());
        board.unsetPiece(Piece.WHITE_PAWN,Square.D5); board.unsetPiece(Piece.BLACK_PAWN,Square.E7);
        moves.get(2).setFenAfter(board.getFen()); match.setCurrentFen(board.getFen());
        var stats=projector.project(match,moves);
        assertThat(stats.capturesComplete()).isTrue();
        assertThat(stats.entries().stream().filter(e->e.captured()!=null)).hasSize(1);
        assertThat(stats.entries().stream().filter(e->!e.isMove()).map(e->e.text())).containsExactly("White Pawn removed at d5","Black Pawn removed at e7");
    }
    @Test void gapsAndBoardMismatchAreExplicit() {
        play("e2e4","d7d5","e4d5");
        var partial=projector.project(match,moves.subList(1,3));
        assertThat(partial.historyComplete()).isFalse(); assertThat(partial.capturesComplete()).isFalse();
        match.setCurrentFen(engine.initialFen());
        assertThat(projector.project(match,moves).capturesComplete()).isFalse();
    }
    @Test void jsonUsesTheUnityIsMoveContract() throws Exception {
        play("e2e4");
        var mapper=new ObjectMapper(); var json=mapper.readTree(mapper.writeValueAsString(projector.project(match,moves)));
        assertThat(json.at("/entries/0/isMove").asBoolean()).isTrue();
        assertThat(json.at("/entries/0/move").isMissingNode()).isTrue();
    }
}
