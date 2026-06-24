package com.chesslan.game.infrastructure.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ChessLibRulesEngine implements ChessRulesEngine {
    @Override
    public String initialFen() {
        return new Board().getFen();
    }

    @Override
    public ChessMoveResult applyMove(List<ChessMoveRecord> previousMoves, ChessMoveCommand command) {
        try {
            Board board = new Board();
            List<Move> replayedMoves = new ArrayList<>();
            for (ChessMoveRecord previous : previousMoves) {
                Move move = createMove(board, previous.from(), previous.to(), previous.promotion());
                if (!board.legalMoves().contains(move) || !board.doMove(move)) {
                    return rejected("CORRUPTED_MATCH_STATE", "Stored match moves cannot be replayed");
                }
                replayedMoves.add(move);
            }

            Move move = createMove(board, command.from(), command.to(), command.promotion());
            if (!board.legalMoves().contains(move)) {
                return rejected("ILLEGAL_MOVE", "The selected move is not legal");
            }
            if (!board.doMove(move)) {
                return rejected("ILLEGAL_MOVE", "The selected move could not be applied");
            }

            MoveList notationMoves = new MoveList();
            notationMoves.addAll(replayedMoves);
            notationMoves.add(move);
            String[] sanMoves = notationMoves.toSanArray();
            String notation = sanMoves[sanMoves.length - 1];

            boolean checkmate = board.isMated();
            boolean draw = !checkmate && board.isDraw();
            String drawReason = drawReason(board);
            return new ChessMoveResult(
                    true,
                    null,
                    null,
                    notation,
                    board.getFen(),
                    board.getSideToMove().name(),
                    board.isKingAttacked(),
                    checkmate,
                    draw,
                    drawReason
            );
        } catch (IllegalArgumentException exception) {
            return rejected("INVALID_MOVE_FORMAT", "Squares must use values such as e2 and e4");
        } catch (Exception exception) {
            return rejected("MOVE_PROCESSING_FAILED", "The move could not be processed");
        }
    }

    private Move createMove(Board board, String from, String to, String promotion) {
        Square fromSquare = Square.valueOf(normalizeSquare(from));
        Square toSquare = Square.valueOf(normalizeSquare(to));
        Piece promotionPiece = promotionPiece(board.getSideToMove(), promotion);
        return promotionPiece == Piece.NONE
                ? new Move(fromSquare, toSquare)
                : new Move(fromSquare, toSquare, promotionPiece);
    }

    private String normalizeSquare(String value) {
        if (value == null || !value.matches("^[a-hA-H][1-8]$")) {
            throw new IllegalArgumentException("Invalid square");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private Piece promotionPiece(Side side, String promotion) {
        if (promotion == null || promotion.isBlank()) {
            return Piece.NONE;
        }
        PieceType type = switch (promotion.trim().toUpperCase(Locale.ROOT)) {
            case "Q", "QUEEN" -> PieceType.QUEEN;
            case "R", "ROOK" -> PieceType.ROOK;
            case "B", "BISHOP" -> PieceType.BISHOP;
            case "N", "KNIGHT" -> PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("Invalid promotion");
        };
        return Piece.make(side, type);
    }

    private String drawReason(Board board) {
        if (!board.isDraw()) {
            return null;
        }
        if (board.isStaleMate()) {
            return "STALEMATE";
        }
        if (board.isInsufficientMaterial()) {
            return "INSUFFICIENT_MATERIAL";
        }
        if (board.isRepetition()) {
            return "REPETITION";
        }
        return "DRAW_RULE";
    }

    private ChessMoveResult rejected(String code, String message) {
        return new ChessMoveResult(false, code, message, null, null, null, false, false, false, null);
    }
}
