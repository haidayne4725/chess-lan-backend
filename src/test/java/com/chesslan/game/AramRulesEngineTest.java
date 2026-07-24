package com.chesslan.game;

import com.chesslan.game.infrastructure.aram.AramBuffId;
import com.chesslan.game.infrastructure.aram.AramMatchState;
import com.chesslan.game.infrastructure.aram.AramRulesEngine;
import com.chesslan.game.infrastructure.aram.AramStateFactory;
import com.chesslan.game.infrastructure.aram.AramTeamState;
import com.chesslan.game.infrastructure.chess.ChessMoveCommand;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class AramRulesEngineTest {
    private final AramRulesEngine engine = new AramRulesEngine();

    @Test
    void deterministicSetupMatchesTheUnityFnvContract() {
        AramMatchState state = new AramStateFactory().create("seed-123");

        assertThat(state.white().buff()).isEqualTo(AramBuffId.Doppelganger);
        assertThat(state.white().swappedKnight()).isEqualTo("g1");
        assertThat(state.white().swappedBishop()).isEqualTo("f1");
        assertThat(state.black().buff()).isEqualTo(AramBuffId.StrongFortress);
    }

    @Test
    void standardRulesStayInLockstepWithChesslibAcrossRandomGames() {
        Random random = new Random(20260722L);
        for (int game = 0; game < 8; game++) {
            Board classic = new Board();
            AramMatchState state = state(
                    team("WHITE", AramBuffId.CommandantPawn, List.of(), "", "", "d1"),
                    team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8")
            );
            List<String> previousFens = new ArrayList<>();

            for (int ply = 0; ply < 60 && !classic.legalMoves().isEmpty(); ply++) {
                List<Move> legalMoves = classic.legalMoves();
                Move move = legalMoves.get(random.nextInt(legalMoves.size()));
                String from = move.getFrom().name().toLowerCase(Locale.ROOT);
                String to = move.getTo().name().toLowerCase(Locale.ROOT);
                String promotion = move.getPromotion() == Piece.NONE
                        ? null
                        : move.getPromotion().getPieceType().name();

                var result = engine.applyMove(classic.getFen(), state, previousFens,
                        new ChessMoveCommand(from, to, promotion));
                assertThat(result.accepted())
                        .as("game %s ply %s move %s%s", game, ply, from, to)
                        .isTrue();
                assertThat(classic.doMove(move)).isTrue();
                assertThat(result.fen())
                        .as("FEN after game %s ply %s move %s%s", game, ply, from, to)
                        .isEqualTo(classic.getFen());

                state = result.aramState();
                previousFens.add(result.fen());
                if (classic.isMated() || classic.isDraw()) {
                    break;
                }
            }
        }
    }

    @Test
    void commandantPawnCanDoubleStepAwayFromItsStartingRank() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.CommandantPawn, List.of("e4"), "", "", "d1"),
                team("BLACK", AramBuffId.FreestyleLeap, List.of(), "", "", "d8")
        );

        var result = engine.applyMove(
                "4k3/8/8/8/4P3/8/8/4K3 w - - 0 1",
                state,
                List.of(),
                new ChessMoveCommand("e4", "e6", null)
        );

        assertThat(result.accepted()).isTrue();
        assertThat(result.fen()).startsWith("4k3/8/4P3/8/8/8/8/4K3 b");
        assertThat(result.aramState().white().commandantPawns()).containsExactly("e6");
    }

    @Test
    void commandantPawnCannotJumpOrGrantPowerToAnUntrackedPawn() {
        AramMatchState tracked = state(
                team("WHITE", AramBuffId.CommandantPawn, List.of("e4"), "", "", "d1"),
                team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8")
        );
        var blocked = engine.applyMove(
                "4k3/8/8/4n3/4P3/8/8/4K3 w - - 0 1",
                tracked, List.of(), new ChessMoveCommand("e4", "e6", null));
        var untracked = engine.applyMove(
                "4k3/8/8/8/4P3/8/8/4K3 w - - 0 1",
                state(team("WHITE", AramBuffId.CommandantPawn, List.of(), "", "", "d1"), tracked.black()),
                List.of(), new ChessMoveCommand("e4", "e6", null));

        assertThat(blocked.accepted()).isFalse();
        assertThat(untracked.accepted()).isFalse();
    }

    @Test
    void commandantDoubleStepCreatesARealEnPassantCapture() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.CommandantPawn, List.of("e4"), "", "", "d1"),
                team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8")
        );
        var doubleStep = engine.applyMove(
                "7k/8/3p4/8/4P3/8/8/K7 w - - 0 1",
                state, List.of(), new ChessMoveCommand("e4", "e6", null));
        var enPassant = engine.applyMove(doubleStep.fen(), doubleStep.aramState(), List.of(doubleStep.fen()),
                new ChessMoveCommand("d6", "e5", null));

        assertThat(doubleStep.accepted()).isTrue();
        assertThat(doubleStep.fen().split(" ")[3]).isEqualTo("e5");
        assertThat(enPassant.accepted()).isTrue();
        assertThat(enPassant.fen()).startsWith("7k/8/8/4p3/8/8/8/K7 w");
    }

    @Test
    void freestyleLeapCanCaptureButNotLandOnAFriendlyPiece() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.FreestyleLeap, List.of(), "", "", "d1"),
                team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8")
        );
        var capture = engine.applyMove(
                "4k3/8/1p6/8/3N4/8/8/4K3 w - - 0 1",
                state, List.of(), new ChessMoveCommand("d4", "b6", null));
        var friendly = engine.applyMove(
                "4k3/8/5P2/8/3N4/8/8/4K3 w - - 0 1",
                state, List.of(), new ChessMoveCommand("d4", "f6", null));

        assertThat(capture.accepted()).isTrue();
        assertThat(friendly.accepted()).isFalse();
        assertThat(friendly.errorCode()).isEqualTo("FRIENDLY_DESTINATION");
    }

    @Test
    void doppelgangerReplacesRatherThanAddsStandardMovement() {
        AramMatchState knightState = state(
                team("WHITE", AramBuffId.Doppelganger, List.of(), "b1", "", "d1"),
                team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8")
        );
        String knightFen = "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1";
        var suppressedKnightMove = engine.applyMove(knightFen, knightState, List.of(),
                new ChessMoveCommand("b1", "c3", null));
        var bishopMove = engine.applyMove(knightFen, knightState, List.of(),
                new ChessMoveCommand("b1", "e4", null));

        AramMatchState bishopState = state(
                team("WHITE", AramBuffId.Doppelganger, List.of(), "", "c1", "d1"),
                knightState.black()
        );
        String bishopFen = "4k3/8/8/8/8/8/8/2B1K3 w - - 0 1";
        var suppressedBishopMove = engine.applyMove(bishopFen, bishopState, List.of(),
                new ChessMoveCommand("c1", "h6", null));
        var knightMove = engine.applyMove(bishopFen, bishopState, List.of(),
                new ChessMoveCommand("c1", "b3", null));

        assertThat(suppressedKnightMove.accepted()).isFalse();
        assertThat(bishopMove.accepted()).isTrue();
        assertThat(suppressedBishopMove.accepted()).isFalse();
        assertThat(knightMove.accepted()).isTrue();
    }

    @Test
    void flyingThunderGodTeleportIsLimitedByCooldown() {
        AramTeamState white = team("WHITE", AramBuffId.FlyingThunderGod, List.of(), "", "", "d1");
        AramMatchState state = state(white, team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8"));
        String fen = "4k3/8/8/8/8/8/8/3QK3 w - - 0 1";

        var accepted = engine.applyMove(fen, state, List.of(), new ChessMoveCommand("d1", "a6", null));

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.notation()).contains("{TP}");
        assertThat(accepted.aramState().white().queenTeleportUses()).isEqualTo(1);
        assertThat(accepted.aramState().white().queenTeleportCooldown()).isEqualTo(5);

        AramTeamState coolingDown = new AramTeamState(
                "WHITE", AramBuffId.FlyingThunderGod, List.of(), "", "", "d1", false, 1, 2);
        var rejected = engine.applyMove(fen, state(coolingDown, state.black()), List.of(),
                new ChessMoveCommand("d1", "a6", null));

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.errorCode()).isEqualTo("ILLEGAL_ARAM_MOVE");
    }

    @Test
    void teleportCannotCaptureOrExceedFiveUsesAndNormalQueenMovesCostNothing() {
        String clearFen = "4k3/8/8/8/8/8/8/3QK3 w - - 0 1";
        AramTeamState available = team("WHITE", AramBuffId.FlyingThunderGod, List.of(), "", "", "d1");
        AramTeamState exhausted = new AramTeamState(
                "WHITE", AramBuffId.FlyingThunderGod, List.of(), "", "", "d1", false, 5, 0);
        AramTeamState black = team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8");

        var capture = engine.applyMove("4k3/8/p7/8/8/8/8/3QK3 w - - 0 1",
                state(available, black), List.of(), new ChessMoveCommand("d1", "a6", null));
        var overLimit = engine.applyMove(clearFen, state(exhausted, black), List.of(),
                new ChessMoveCommand("d1", "a6", null));
        var ordinary = engine.applyMove(clearFen, state(available, black), List.of(),
                new ChessMoveCommand("d1", "d4", null));

        assertThat(capture.accepted()).isFalse();
        assertThat(overLimit.accepted()).isFalse();
        assertThat(ordinary.accepted()).isTrue();
        assertThat(ordinary.aramState().white().queenTeleportUses()).isZero();
        assertThat(ordinary.aramState().white().queenTeleportCooldown()).isZero();
    }

    @Test
    void teleportCooldownTicksOnlyWhenThatTeamStartsItsNextTurn() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.FlyingThunderGod, List.of(), "", "", "d1"),
                team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8")
        );
        var teleport = engine.applyMove(
                "4k3/8/8/8/8/8/8/3QK3 w - - 0 1",
                state, List.of(), new ChessMoveCommand("d1", "a6", null));
        var blackMove = engine.applyMove(teleport.fen(), teleport.aramState(), List.of(teleport.fen()),
                new ChessMoveCommand("e8", "e7", null));

        assertThat(teleport.aramState().white().queenTeleportCooldown()).isEqualTo(5);
        assertThat(blackMove.accepted()).isTrue();
        assertThat(blackMove.aramState().white().queenTeleportCooldown()).isEqualTo(4);
    }

    @Test
    void strongFortressCanCastleThroughAnAttackedTransitSquare() {
        String fen = "k4r2/8/8/8/8/8/8/4K2R w K - 0 1";
        AramTeamState black = team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8");

        var ordinary = engine.applyMove(fen,
                state(team("WHITE", AramBuffId.CommandantPawn, List.of(), "", "", "d1"), black),
                List.of(), new ChessMoveCommand("e1", "g1", null));
        var fortified = engine.applyMove(fen,
                state(team("WHITE", AramBuffId.StrongFortress, List.of(), "", "", "d1"), black),
                List.of(), new ChessMoveCommand("e1", "g1", null));

        assertThat(ordinary.accepted()).isFalse();
        assertThat(fortified.accepted()).isTrue();
        assertThat(fortified.notation()).isEqualTo("O-O");
    }

    @Test
    void strongFortressStillRequiresRightsClearPathAndSafeDestination() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.StrongFortress, List.of(), "", "", "d1"),
                team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8")
        );
        var noRights = engine.applyMove("k7/8/8/8/8/8/8/4K2R w - - 0 1",
                state, List.of(), new ChessMoveCommand("e1", "g1", null));
        var blockedPath = engine.applyMove("k7/8/8/8/8/8/8/4KN1R w K - 0 1",
                state, List.of(), new ChessMoveCommand("e1", "g1", null));
        var attackedDestination = engine.applyMove("k5r1/8/8/8/8/8/8/4K2R w K - 0 1",
                state, List.of(), new ChessMoveCommand("e1", "g1", null));

        assertThat(noRights.accepted()).isFalse();
        assertThat(blockedPath.accepted()).isFalse();
        assertThat(attackedDestination.accepted()).isFalse();
        assertThat(attackedDestination.errorCode()).isEqualTo("KING_LEFT_IN_CHECK");
    }

    @Test
    void suicideBomberRemovesAdjacentNonKingPiecesButKeepsCapturer() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.FreestyleLeap, List.of(), "", "", "d1"),
                team("BLACK", AramBuffId.SuicideBomber, List.of(), "", "", "d5")
        );

        var result = engine.applyMove(
                "4k3/8/3r4/3q4/8/8/8/3RK3 w - - 0 1",
                state,
                List.of(),
                new ChessMoveCommand("d1", "d5", null)
        );

        assertThat(result.accepted()).isTrue();
        assertThat(result.fen()).startsWith("4k3/8/8/3R4/8/8/8/4K3 b");
        assertThat(result.aramState().black().suicideBomberUsed()).isTrue();
        assertThat(result.aramState().black().originalQueen()).isEmpty();
    }

    @Test
    void suicideBomberKeepsKingsStripsDestroyedRookRightsAndOnlyTriggersOnce() {
        AramTeamState bomber = team("BLACK", AramBuffId.SuicideBomber, List.of(), "", "", "b2");
        AramMatchState state = state(
                team("WHITE", AramBuffId.CommandantPawn, List.of(), "", "", "d1"), bomber);
        var destroysRook = engine.applyMove(
                "7k/8/8/8/8/8/1q6/RR2K3 w Q - 0 1",
                state, List.of(), new ChessMoveCommand("b1", "b2", null));

        AramTeamState alreadyUsed = new AramTeamState(
                "BLACK", AramBuffId.SuicideBomber, List.of(), "", "", "d5", true, 0, 0);
        var noSecondExplosion = engine.applyMove(
                "k7/8/3r4/3q4/4K3/8/8/3R4 w - - 0 1",
                state(state.white(), alreadyUsed), List.of(), new ChessMoveCommand("d1", "d5", null));
        var kingImmune = engine.applyMove(
                "k7/8/8/3q4/4K3/8/8/3R4 w - - 0 1",
                state(state.white(), team("BLACK", AramBuffId.SuicideBomber, List.of(), "", "", "d5")),
                List.of(), new ChessMoveCommand("d1", "d5", null));

        assertThat(destroysRook.accepted()).isTrue();
        assertThat(destroysRook.fen().split(" ")[2]).isEqualTo("-");
        assertThat(destroysRook.fen()).doesNotContain("R3K");
        assertThat(noSecondExplosion.accepted()).isTrue();
        assertThat(noSecondExplosion.fen()).startsWith("k7/8/3r4/3R4");
        assertThat(kingImmune.accepted()).isTrue();
        assertThat(kingImmune.fen()).contains("4K3");
    }

    @Test
    void aramAttackPatternsParticipateInKingSafety() {
        String fen = "k7/8/8/2n5/8/4K3/8/7R w - - 0 1";
        AramTeamState white = team("WHITE", AramBuffId.CommandantPawn, List.of(), "", "", "d1");
        var ordinary = engine.applyMove(fen,
                state(white, team("BLACK", AramBuffId.CommandantPawn, List.of(), "", "", "d8")),
                List.of(), new ChessMoveCommand("h1", "h2", null));
        var freestyleAttack = engine.applyMove(fen,
                state(white, team("BLACK", AramBuffId.FreestyleLeap, List.of(), "", "", "d8")),
                List.of(), new ChessMoveCommand("h1", "h2", null));

        assertThat(ordinary.accepted()).isTrue();
        assertThat(freestyleAttack.accepted()).isFalse();
        assertThat(freestyleAttack.errorCode()).isEqualTo("KING_LEFT_IN_CHECK");
    }

    @Test
    void promotionClearsCommandantTrackingAndInvalidInputsFailClosed() {
        AramMatchState state = state(
                team("WHITE", AramBuffId.CommandantPawn, List.of("e7"), "", "", "d1"),
                team("BLACK", AramBuffId.StrongFortress, List.of(), "", "", "d8")
        );
        var promoted = engine.applyMove("k7/4P3/8/8/8/8/8/7K w - - 0 1",
                state, List.of(), new ChessMoveCommand("e7", "e8", "KNIGHT"));
        var invalidSquare = engine.applyMove("k7/8/8/8/8/8/8/7K w - - 0 1",
                state, List.of(), new ChessMoveCommand("z9", "e4", null));
        var missingState = engine.applyMove("k7/8/8/8/8/8/8/7K w - - 0 1",
                null, List.of(), new ChessMoveCommand("h1", "h2", null));

        assertThat(promoted.accepted()).isTrue();
        assertThat(promoted.fen()).startsWith("k3N3");
        assertThat(promoted.aramState().white().commandantPawns()).isEmpty();
        assertThat(invalidSquare.accepted()).isFalse();
        assertThat(invalidSquare.errorCode()).isEqualTo("INVALID_MOVE_FORMAT");
        assertThat(missingState.accepted()).isFalse();
        assertThat(missingState.errorCode()).isEqualTo("ARAM_STATE_REQUIRED");
    }

    private AramMatchState state(AramTeamState white, AramTeamState black) {
        return new AramMatchState(1, "test-seed", white, black);
    }

    private AramTeamState team(String name,
                               AramBuffId buff,
                               List<String> pawns,
                               String knight,
                               String bishop,
                               String queen) {
        return new AramTeamState(name, buff, pawns, knight, bishop, queen, false, 0, 0);
    }
}
