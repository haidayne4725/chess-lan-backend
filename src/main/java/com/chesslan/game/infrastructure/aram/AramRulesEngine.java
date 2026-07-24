package com.chesslan.game.infrastructure.aram;

import com.chesslan.game.infrastructure.chess.ChessMoveCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AramRulesEngine {
    private static final int SIZE = 8;

    public AramMoveResult applyMove(String fen,
                                    AramMatchState aramState,
                                    List<String> previousFens,
                                    ChessMoveCommand command) {
        try {
            if (aramState == null || aramState.white() == null || aramState.black() == null) {
                return AramMoveResult.rejected("ARAM_STATE_REQUIRED", "Authoritative ARAM state is missing");
            }
            FenBoard board = FenBoard.parse(fen);
            Position from = Position.parse(command.from());
            Position to = Position.parse(command.to());
            AppliedMove applied = tryApply(board, aramState, from, to, command.promotion(), true, previousFens);
            if (!applied.accepted) {
                return AramMoveResult.rejected(applied.errorCode, applied.message);
            }
            return new AramMoveResult(
                    true,
                    null,
                    null,
                    applied.notation,
                    applied.board.toFen(),
                    applied.board.whiteToMove ? "WHITE" : "BLACK",
                    applied.check,
                    applied.checkmate,
                    applied.draw,
                    applied.drawReason,
                    applied.aramState
            );
        } catch (IllegalArgumentException exception) {
            return AramMoveResult.rejected("INVALID_MOVE_FORMAT", exception.getMessage());
        } catch (Exception exception) {
            return AramMoveResult.rejected("MOVE_PROCESSING_FAILED", "The ARAM move could not be processed");
        }
    }

    private AppliedMove tryApply(FenBoard original,
                                 AramMatchState originalState,
                                 Position from,
                                 Position to,
                                 String promotion,
                                 boolean evaluateTerminal,
                                 List<String> previousFens) {
        if (from.equals(to)) {
            return AppliedMove.rejected("ILLEGAL_ARAM_MOVE", "Source and destination must differ");
        }
        char moving = original.get(from);
        boolean movingWhite = Character.isUpperCase(moving);
        if (moving == '.' || movingWhite != original.whiteToMove) {
            return AppliedMove.rejected("NOT_YOUR_PIECE", "The source square does not contain a movable piece");
        }
        char target = original.get(to);
        if (target != '.' && Character.isUpperCase(target) == movingWhite) {
            return AppliedMove.rejected("FRIENDLY_DESTINATION", "A friendly piece occupies the destination");
        }
        if (Character.toLowerCase(target) == 'k') {
            return AppliedMove.rejected("KING_CAPTURE_FORBIDDEN", "Kings cannot be captured");
        }

        String team = movingWhite ? "WHITE" : "BLACK";
        AramTeamState teamState = teamState(originalState, team);
        MoveKind kind = legalMoveKind(original, originalState, teamState, moving, from, to);
        if (kind == MoveKind.ILLEGAL) {
            return AppliedMove.rejected("ILLEGAL_ARAM_MOVE", "The selected move is not legal for the active ARAM rules");
        }

        FenBoard board = original.copy();
        AramMatchState state = originalState;
        boolean pawnMove = Character.toLowerCase(moving) == 'p';
        boolean capture = target != '.';
        char capturedPiece = target;
        Position capturedAt = target == '.' ? null : to;

        if (kind == MoveKind.EN_PASSANT) {
            capturedAt = new Position(to.x, from.y);
            capturedPiece = board.get(capturedAt);
            board.set(capturedAt, '.');
            capture = true;
        }

        board.set(from, '.');
        board.set(to, moving);

        if (kind == MoveKind.CASTLE_KING || kind == MoveKind.CASTLE_QUEEN) {
            int rookFromX = kind == MoveKind.CASTLE_KING ? 7 : 0;
            int rookToX = kind == MoveKind.CASTLE_KING ? 5 : 3;
            Position rookFrom = new Position(rookFromX, from.y);
            Position rookTo = new Position(rookToX, from.y);
            char rook = board.get(rookFrom);
            board.set(rookFrom, '.');
            board.set(rookTo, rook);
        }

        char placedPiece = applyPromotion(moving, to, promotion);
        board.set(to, placedPiece);
        state = moveTrackedPiece(state, team, from.square(), to.square());
        if (pawnMove && Character.toLowerCase(placedPiece) != 'p') {
            state = removeCapturedTrackedPiece(state, team, to.square());
        }
        state = removeCapturedTrackedPiece(state, opposite(team), capturedAt == null ? "" : capturedAt.square());

        if (kind == MoveKind.TELEPORT) {
            AramTeamState updated = teamState(state, team);
            updated = copyTeam(updated, updated.commandantPawns(), updated.swappedKnight(), updated.swappedBishop(),
                    updated.originalQueen(), updated.suicideBomberUsed(), updated.queenTeleportUses() + 1, 5);
            state = replaceTeam(state, team, updated);
        }

        if (Character.toLowerCase(capturedPiece) == 'q') {
            AramTeamState capturedState = teamState(state, opposite(team));
            boolean originalQueen = capturedAt != null && capturedAt.square().equalsIgnoreCase(
                    teamState(originalState, opposite(team)).originalQueen());
            if (originalQueen && capturedState.buff() == AramBuffId.SuicideBomber && !capturedState.suicideBomberUsed()) {
                state = detonate(board, state, opposite(team), to);
            }
        }

        board.castlingRights = sanitizeCastlingRights(board,
                updatedCastlingRights(original.castlingRights, moving, from, capturedPiece, capturedAt));
        board.enPassant = pawnMove && Math.abs(to.y - from.y) == 2
                ? new Position(from.x, (from.y + to.y) / 2).square()
                : "-";
        board.halfMoveClock = pawnMove || capture ? 0 : original.halfMoveClock + 1;
        board.fullMoveNumber = original.fullMoveNumber + (movingWhite ? 0 : 1);
        board.whiteToMove = !original.whiteToMove;
        state = tickCooldown(state, board.whiteToMove ? "WHITE" : "BLACK");

        if (isKingAttacked(board, state, team)) {
            return AppliedMove.rejected("KING_LEFT_IN_CHECK", "The move leaves the king in check");
        }

        boolean check = isKingAttacked(board, state, opposite(team));
        boolean checkmate = false;
        boolean draw = false;
        String drawReason = null;
        if (evaluateTerminal) {
            boolean anyLegal = hasAnyLegalMove(board, state);
            checkmate = check && !anyLegal;
            if (!checkmate && !anyLegal) {
                draw = true;
                drawReason = "STALEMATE";
            } else if (!checkmate && board.halfMoveClock >= 100) {
                draw = true;
                drawReason = "DRAW_RULE";
            } else if (!checkmate && insufficientMaterial(board)) {
                draw = true;
                drawReason = "INSUFFICIENT_MATERIAL";
            } else if (!checkmate && isThreefold(board, previousFens)) {
                draw = true;
                drawReason = "REPETITION";
            }
        }

        String notation = notation(moving, from, to, capture, kind, placedPiece, check, checkmate);
        return AppliedMove.accepted(board, state, notation, check, checkmate, draw, drawReason);
    }

    private MoveKind legalMoveKind(FenBoard board,
                                   AramMatchState state,
                                   AramTeamState teamState,
                                   char piece,
                                   Position from,
                                   Position to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        char type = Character.toLowerCase(piece);
        String fromSquare = from.square();

        if (teamState.buff() == AramBuffId.Doppelganger) {
            if (type == 'n' && fromSquare.equalsIgnoreCase(teamState.swappedKnight())) {
                return Math.abs(dx) == Math.abs(dy) && pathClear(board, from, to) ? MoveKind.DOPPELGANGER : MoveKind.ILLEGAL;
            }
            if (type == 'b' && fromSquare.equalsIgnoreCase(teamState.swappedBishop())) {
                return knight(dx, dy) ? MoveKind.DOPPELGANGER : MoveKind.ILLEGAL;
            }
        }

        return switch (type) {
            case 'p' -> pawnMoveKind(board, teamState, piece, from, to);
            case 'n' -> knight(dx, dy) ||
                    (teamState.buff() == AramBuffId.FreestyleLeap && Math.abs(dx) == 2 && Math.abs(dy) == 2)
                    ? MoveKind.NORMAL : MoveKind.ILLEGAL;
            case 'b' -> Math.abs(dx) == Math.abs(dy) && pathClear(board, from, to) ? MoveKind.NORMAL : MoveKind.ILLEGAL;
            case 'r' -> (dx == 0 || dy == 0) && pathClear(board, from, to) ? MoveKind.NORMAL : MoveKind.ILLEGAL;
            case 'q' -> queenMoveKind(board, teamState, from, to);
            case 'k' -> kingMoveKind(board, state, teamState, piece, from, to);
            default -> MoveKind.ILLEGAL;
        };
    }

    private MoveKind pawnMoveKind(FenBoard board, AramTeamState teamState, char pawn, Position from, Position to) {
        boolean white = Character.isUpperCase(pawn);
        int direction = white ? 1 : -1;
        int startRank = white ? 1 : 6;
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        char target = board.get(to);
        if (dx == 0 && dy == direction && target == '.') {
            return MoveKind.NORMAL;
        }
        if (dx == 0 && dy == direction * 2 && target == '.' && board.get(new Position(from.x, from.y + direction)) == '.') {
            boolean standard = from.y == startRank;
            boolean commandant = teamState.buff() == AramBuffId.CommandantPawn &&
                    containsSquare(teamState.commandantPawns(), from.square());
            return standard || commandant ? MoveKind.NORMAL : MoveKind.ILLEGAL;
        }
        if (Math.abs(dx) == 1 && dy == direction) {
            if (target != '.' && Character.isUpperCase(target) != white) {
                return MoveKind.NORMAL;
            }
            if (to.square().equalsIgnoreCase(board.enPassant)) {
                char adjacent = board.get(new Position(to.x, from.y));
                if (Character.toLowerCase(adjacent) == 'p' && Character.isUpperCase(adjacent) != white) {
                    return MoveKind.EN_PASSANT;
                }
            }
        }
        return MoveKind.ILLEGAL;
    }

    private MoveKind queenMoveKind(FenBoard board, AramTeamState teamState, Position from, Position to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        boolean normalShape = dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy);
        if (normalShape && pathClear(board, from, to)) {
            return MoveKind.NORMAL;
        }
        if (teamState.buff() == AramBuffId.FlyingThunderGod &&
                from.square().equalsIgnoreCase(teamState.originalQueen()) &&
                board.get(to) == '.' &&
                teamState.queenTeleportUses() < 5 &&
                teamState.queenTeleportCooldown() <= 0) {
            return MoveKind.TELEPORT;
        }
        return MoveKind.ILLEGAL;
    }

    private MoveKind kingMoveKind(FenBoard board,
                                  AramMatchState state,
                                  AramTeamState teamState,
                                  char king,
                                  Position from,
                                  Position to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        if (Math.max(Math.abs(dx), Math.abs(dy)) == 1) {
            return MoveKind.NORMAL;
        }
        if (dy != 0 || Math.abs(dx) != 2) {
            return MoveKind.ILLEGAL;
        }
        boolean white = Character.isUpperCase(king);
        int homeRank = white ? 0 : 7;
        if (from.x != 4 || from.y != homeRank || to.y != homeRank) {
            return MoveKind.ILLEGAL;
        }
        boolean kingSide = dx > 0;
        char right = white ? (kingSide ? 'K' : 'Q') : (kingSide ? 'k' : 'q');
        if (board.castlingRights.indexOf(right) < 0) {
            return MoveKind.ILLEGAL;
        }
        int rookX = kingSide ? 7 : 0;
        char rook = board.get(new Position(rookX, homeRank));
        if (Character.toLowerCase(rook) != 'r' || Character.isUpperCase(rook) != white) {
            return MoveKind.ILLEGAL;
        }
        int step = kingSide ? 1 : -1;
        for (int x = from.x + step; x != rookX; x += step) {
            if (board.get(new Position(x, homeRank)) != '.') {
                return MoveKind.ILLEGAL;
            }
        }
        boolean strongFortress = teamState.buff() == AramBuffId.StrongFortress;
        if (!strongFortress) {
            String team = white ? "WHITE" : "BLACK";
            if (isKingAttacked(board, state, team) ||
                    isSquareAttacked(board, state, new Position(from.x + step, homeRank), opposite(team)) ||
                    isSquareAttacked(board, state, to, opposite(team))) {
                return MoveKind.ILLEGAL;
            }
        }
        return kingSide ? MoveKind.CASTLE_KING : MoveKind.CASTLE_QUEEN;
    }

    private boolean hasAnyLegalMove(FenBoard board, AramMatchState state) {
        boolean sideWhite = board.whiteToMove;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                char piece = board.cells[x][y];
                if (piece == '.' || Character.isUpperCase(piece) != sideWhite) {
                    continue;
                }
                Position from = new Position(x, y);
                for (int tx = 0; tx < SIZE; tx++) {
                    for (int ty = 0; ty < SIZE; ty++) {
                        Position to = new Position(tx, ty);
                        String promotion = Character.toLowerCase(piece) == 'p' && (ty == 0 || ty == 7) ? "QUEEN" : null;
                        if (tryApply(board, state, from, to, promotion, false, List.of()).accepted) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isKingAttacked(FenBoard board, AramMatchState state, String team) {
        char king = "WHITE".equals(team) ? 'K' : 'k';
        Position square = board.find(king);
        return square == null || isSquareAttacked(board, state, square, opposite(team));
    }

    private boolean isSquareAttacked(FenBoard board, AramMatchState state, Position square, String attackerTeam) {
        boolean attackerWhite = "WHITE".equals(attackerTeam);
        AramTeamState attackerState = teamState(state, attackerTeam);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                char piece = board.cells[x][y];
                if (piece == '.' || Character.isUpperCase(piece) != attackerWhite) {
                    continue;
                }
                Position from = new Position(x, y);
                int dx = square.x - x;
                int dy = square.y - y;
                char type = Character.toLowerCase(piece);
                if (attackerState.buff() == AramBuffId.Doppelganger) {
                    if (type == 'n' && from.square().equalsIgnoreCase(attackerState.swappedKnight())) {
                        if (Math.abs(dx) == Math.abs(dy) && pathClear(board, from, square)) return true;
                        continue;
                    }
                    if (type == 'b' && from.square().equalsIgnoreCase(attackerState.swappedBishop())) {
                        if (knight(dx, dy)) return true;
                        continue;
                    }
                }
                boolean attacks = switch (type) {
                    case 'p' -> Math.abs(dx) == 1 && dy == (attackerWhite ? 1 : -1);
                    case 'n' -> knight(dx, dy) ||
                            (attackerState.buff() == AramBuffId.FreestyleLeap && Math.abs(dx) == 2 && Math.abs(dy) == 2);
                    case 'b' -> Math.abs(dx) == Math.abs(dy) && pathClear(board, from, square);
                    case 'r' -> (dx == 0 || dy == 0) && pathClear(board, from, square);
                    case 'q' -> (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy)) && pathClear(board, from, square);
                    case 'k' -> Math.max(Math.abs(dx), Math.abs(dy)) == 1;
                    default -> false;
                };
                if (attacks) return true;
            }
        }
        return false;
    }

    private AramMatchState detonate(FenBoard board, AramMatchState state, String queenTeam, Position center) {
        AramTeamState queenState = teamState(state, queenTeam);
        queenState = copyTeam(queenState, queenState.commandantPawns(), queenState.swappedKnight(), queenState.swappedBishop(),
                "", true, queenState.queenTeleportUses(), queenState.queenTeleportCooldown());
        state = replaceTeam(state, queenTeam, queenState);
        for (int x = center.x - 1; x <= center.x + 1; x++) {
            for (int y = center.y - 1; y <= center.y + 1; y++) {
                Position position = new Position(x, y);
                if (!position.inside() || position.equals(center)) continue;
                char victim = board.get(position);
                if (victim == '.' || Character.toLowerCase(victim) == 'k') continue;
                board.set(position, '.');
                state = removeCapturedTrackedPiece(state,
                        Character.isUpperCase(victim) ? "WHITE" : "BLACK",
                        position.square());
            }
        }
        return state;
    }

    private AramMatchState moveTrackedPiece(AramMatchState state, String team, String from, String to) {
        AramTeamState current = teamState(state, team);
        List<String> pawns = replaceSquare(current.commandantPawns(), from, to);
        String knight = replaceSquare(current.swappedKnight(), from, to);
        String bishop = replaceSquare(current.swappedBishop(), from, to);
        String queen = replaceSquare(current.originalQueen(), from, to);
        return replaceTeam(state, team, copyTeam(current, pawns, knight, bishop, queen,
                current.suicideBomberUsed(), current.queenTeleportUses(), current.queenTeleportCooldown()));
    }

    private AramMatchState removeCapturedTrackedPiece(AramMatchState state, String team, String square) {
        if (square == null || square.isBlank()) return state;
        AramTeamState current = teamState(state, team);
        List<String> pawns = current.commandantPawns().stream()
                .filter(value -> !value.equalsIgnoreCase(square)).toList();
        String knight = clearSquare(current.swappedKnight(), square);
        String bishop = clearSquare(current.swappedBishop(), square);
        String queen = clearSquare(current.originalQueen(), square);
        return replaceTeam(state, team, copyTeam(current, pawns, knight, bishop, queen,
                current.suicideBomberUsed(), current.queenTeleportUses(), current.queenTeleportCooldown()));
    }

    private AramMatchState tickCooldown(AramMatchState state, String team) {
        AramTeamState current = teamState(state, team);
        if (current.queenTeleportCooldown() <= 0) return state;
        AramTeamState updated = copyTeam(current, current.commandantPawns(), current.swappedKnight(), current.swappedBishop(),
                current.originalQueen(), current.suicideBomberUsed(), current.queenTeleportUses(),
                current.queenTeleportCooldown() - 1);
        return replaceTeam(state, team, updated);
    }

    private AramTeamState copyTeam(AramTeamState source,
                                   List<String> pawns,
                                   String knight,
                                   String bishop,
                                   String queen,
                                   boolean bomberUsed,
                                   int teleportUses,
                                   int teleportCooldown) {
        return new AramTeamState(source.team(), source.buff(), pawns, knight, bishop, queen,
                bomberUsed, teleportUses, teleportCooldown);
    }

    private AramMatchState replaceTeam(AramMatchState state, String team, AramTeamState replacement) {
        return "WHITE".equals(team)
                ? new AramMatchState(state.version() + 1, state.seed(), replacement, state.black())
                : new AramMatchState(state.version() + 1, state.seed(), state.white(), replacement);
    }

    private AramTeamState teamState(AramMatchState state, String team) {
        return "WHITE".equals(team) ? state.white() : state.black();
    }

    private String opposite(String team) {
        return "WHITE".equals(team) ? "BLACK" : "WHITE";
    }

    private char applyPromotion(char moving, Position to, String promotion) {
        if (Character.toLowerCase(moving) != 'p' || (to.y != 0 && to.y != 7)) {
            if (promotion != null && !promotion.isBlank()) {
                throw new IllegalArgumentException("Promotion is only valid on the final pawn rank");
            }
            return moving;
        }
        char promoted = switch (promotion == null ? "QUEEN" : promotion.trim().toUpperCase(Locale.ROOT)) {
            case "Q", "QUEEN" -> 'q';
            case "R", "ROOK" -> 'r';
            case "B", "BISHOP" -> 'b';
            case "N", "KNIGHT" -> 'n';
            default -> throw new IllegalArgumentException("Invalid promotion piece");
        };
        return Character.isUpperCase(moving) ? Character.toUpperCase(promoted) : promoted;
    }

    private String updatedCastlingRights(String rights, char moving, Position from, char captured, Position capturedAt) {
        String updated = rights == null ? "" : rights.replace("-", "");
        if (moving == 'K') updated = updated.replace("K", "").replace("Q", "");
        if (moving == 'k') updated = updated.replace("k", "").replace("q", "");
        if (moving == 'R' && from.square().equals("h1")) updated = updated.replace("K", "");
        if (moving == 'R' && from.square().equals("a1")) updated = updated.replace("Q", "");
        if (moving == 'r' && from.square().equals("h8")) updated = updated.replace("k", "");
        if (moving == 'r' && from.square().equals("a8")) updated = updated.replace("q", "");
        if (capturedAt != null && captured == 'R' && capturedAt.square().equals("h1")) updated = updated.replace("K", "");
        if (capturedAt != null && captured == 'R' && capturedAt.square().equals("a1")) updated = updated.replace("Q", "");
        if (capturedAt != null && captured == 'r' && capturedAt.square().equals("h8")) updated = updated.replace("k", "");
        if (capturedAt != null && captured == 'r' && capturedAt.square().equals("a8")) updated = updated.replace("q", "");
        return updated.isBlank() ? "-" : updated;
    }

    private String sanitizeCastlingRights(FenBoard board, String rights) {
        String updated = rights == null ? "" : rights.replace("-", "");
        if (board.get(new Position(4, 0)) != 'K') updated = updated.replace("K", "").replace("Q", "");
        if (board.get(new Position(7, 0)) != 'R') updated = updated.replace("K", "");
        if (board.get(new Position(0, 0)) != 'R') updated = updated.replace("Q", "");
        if (board.get(new Position(4, 7)) != 'k') updated = updated.replace("k", "").replace("q", "");
        if (board.get(new Position(7, 7)) != 'r') updated = updated.replace("k", "");
        if (board.get(new Position(0, 7)) != 'r') updated = updated.replace("q", "");
        return updated.isBlank() ? "-" : updated;
    }

    private String notation(char moving, Position from, Position to, boolean capture, MoveKind kind,
                            char placedPiece, boolean check, boolean checkmate) {
        if (kind == MoveKind.CASTLE_KING) return "O-O" + (checkmate ? "#" : check ? "+" : "");
        if (kind == MoveKind.CASTLE_QUEEN) return "O-O-O" + (checkmate ? "#" : check ? "+" : "");
        char type = Character.toLowerCase(moving);
        StringBuilder value = new StringBuilder();
        if (type != 'p') value.append(Character.toUpperCase(type));
        else if (capture) value.append(from.square().charAt(0));
        if (capture) value.append('x');
        value.append(to.square());
        if (type == 'p' && Character.toLowerCase(placedPiece) != 'p') {
            value.append('=').append(Character.toUpperCase(placedPiece));
        }
        if (kind == MoveKind.TELEPORT) value.append("{TP}");
        if (checkmate) value.append('#');
        else if (check) value.append('+');
        return value.toString();
    }

    private boolean insufficientMaterial(FenBoard board) {
        List<Character> pieces = new ArrayList<>();
        List<Position> positions = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < SIZE; y++) {
            char piece = board.cells[x][y];
            if (piece != '.' && Character.toLowerCase(piece) != 'k') {
                pieces.add(piece);
                positions.add(new Position(x, y));
            }
        }
        if (pieces.isEmpty()) return true;
        if (pieces.size() == 1) return "bn".indexOf(Character.toLowerCase(pieces.get(0))) >= 0;
        if (pieces.size() != 2 || Character.toLowerCase(pieces.get(0)) != 'b' || Character.toLowerCase(pieces.get(1)) != 'b') {
            return false;
        }
        return (positions.get(0).x + positions.get(0).y) % 2 == (positions.get(1).x + positions.get(1).y) % 2;
    }

    private boolean isThreefold(FenBoard board, List<String> previousFens) {
        String key = board.positionKey();
        int count = 1;
        if (previousFens != null) {
            for (String fen : previousFens) {
                try {
                    if (FenBoard.parse(fen).positionKey().equals(key)) count++;
                } catch (Exception ignored) {
                    // A malformed historical FEN is handled when that move is loaded elsewhere.
                }
            }
        }
        return count >= 3;
    }

    private boolean pathClear(FenBoard board, Position from, Position to) {
        int stepX = Integer.compare(to.x, from.x);
        int stepY = Integer.compare(to.y, from.y);
        int x = from.x + stepX;
        int y = from.y + stepY;
        while (x != to.x || y != to.y) {
            if (board.cells[x][y] != '.') return false;
            x += stepX;
            y += stepY;
        }
        return true;
    }

    private boolean knight(int dx, int dy) {
        int x = Math.abs(dx);
        int y = Math.abs(dy);
        return (x == 1 && y == 2) || (x == 2 && y == 1);
    }

    private boolean containsSquare(List<String> values, String square) {
        return values != null && values.stream().anyMatch(value -> value.equalsIgnoreCase(square));
    }

    private List<String> replaceSquare(List<String> values, String from, String to) {
        if (values == null) return List.of();
        return values.stream().map(value -> value.equalsIgnoreCase(from) ? to : value).toList();
    }

    private String replaceSquare(String value, String from, String to) {
        return value != null && value.equalsIgnoreCase(from) ? to : value;
    }

    private String clearSquare(String value, String square) {
        return value != null && value.equalsIgnoreCase(square) ? "" : value;
    }

    private enum MoveKind {
        ILLEGAL,
        NORMAL,
        EN_PASSANT,
        CASTLE_KING,
        CASTLE_QUEEN,
        DOPPELGANGER,
        TELEPORT
    }

    private record Position(int x, int y) {
        static Position parse(String square) {
            if (square == null || !square.matches("(?i)^[a-h][1-8]$")) {
                throw new IllegalArgumentException("Squares must use values such as e2 and e4");
            }
            String normalized = square.toLowerCase(Locale.ROOT);
            return new Position(normalized.charAt(0) - 'a', normalized.charAt(1) - '1');
        }

        boolean inside() {
            return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
        }

        String square() {
            return "" + (char) ('a' + x) + (char) ('1' + y);
        }
    }

    private static final class FenBoard {
        private final char[][] cells = new char[SIZE][SIZE];
        private boolean whiteToMove;
        private String castlingRights;
        private String enPassant;
        private int halfMoveClock;
        private int fullMoveNumber;

        private FenBoard() {
            for (int x = 0; x < SIZE; x++) for (int y = 0; y < SIZE; y++) cells[x][y] = '.';
        }

        static FenBoard parse(String fen) {
            if (fen == null || fen.isBlank()) throw new IllegalArgumentException("FEN is required");
            String[] fields = fen.trim().split("\\s+");
            if (fields.length < 2) throw new IllegalArgumentException("Invalid FEN");
            String[] ranks = fields[0].split("/");
            if (ranks.length != SIZE) throw new IllegalArgumentException("Invalid FEN board");
            FenBoard board = new FenBoard();
            for (int rankIndex = 0; rankIndex < SIZE; rankIndex++) {
                int x = 0;
                int y = 7 - rankIndex;
                for (char symbol : ranks[rankIndex].toCharArray()) {
                    if (Character.isDigit(symbol)) x += symbol - '0';
                    else {
                        if (x >= SIZE || "pnbrqkPNBRQK".indexOf(symbol) < 0) throw new IllegalArgumentException("Invalid FEN piece");
                        board.cells[x++][y] = symbol;
                    }
                }
                if (x != SIZE) throw new IllegalArgumentException("Invalid FEN rank");
            }
            board.whiteToMove = !"b".equalsIgnoreCase(fields[1]);
            board.castlingRights = fields.length > 2 ? fields[2] : "-";
            board.enPassant = fields.length > 3 ? fields[3].toLowerCase(Locale.ROOT) : "-";
            board.halfMoveClock = fields.length > 4 ? Integer.parseInt(fields[4]) : 0;
            board.fullMoveNumber = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;
            return board;
        }

        FenBoard copy() {
            FenBoard copy = new FenBoard();
            for (int x = 0; x < SIZE; x++) System.arraycopy(cells[x], 0, copy.cells[x], 0, SIZE);
            copy.whiteToMove = whiteToMove;
            copy.castlingRights = castlingRights;
            copy.enPassant = enPassant;
            copy.halfMoveClock = halfMoveClock;
            copy.fullMoveNumber = fullMoveNumber;
            return copy;
        }

        char get(Position position) {
            return position.inside() ? cells[position.x][position.y] : '.';
        }

        void set(Position position, char piece) {
            if (!position.inside()) throw new IllegalArgumentException("Square outside board");
            cells[position.x][position.y] = piece;
        }

        Position find(char piece) {
            for (int x = 0; x < SIZE; x++) for (int y = 0; y < SIZE; y++) if (cells[x][y] == piece) return new Position(x, y);
            return null;
        }

        String positionKey() {
            return boardPart() + " " + (whiteToMove ? "w" : "b") + " " + castlingRights + " " + enPassant;
        }

        String toFen() {
            return positionKey() + " " + halfMoveClock + " " + fullMoveNumber;
        }

        private String boardPart() {
            StringBuilder value = new StringBuilder();
            for (int y = 7; y >= 0; y--) {
                if (y < 7) value.append('/');
                int empty = 0;
                for (int x = 0; x < SIZE; x++) {
                    char piece = cells[x][y];
                    if (piece == '.') empty++;
                    else {
                        if (empty > 0) value.append(empty);
                        empty = 0;
                        value.append(piece);
                    }
                }
                if (empty > 0) value.append(empty);
            }
            return value.toString();
        }
    }

    private static final class AppliedMove {
        private final boolean accepted;
        private final String errorCode;
        private final String message;
        private final FenBoard board;
        private final AramMatchState aramState;
        private final String notation;
        private final boolean check;
        private final boolean checkmate;
        private final boolean draw;
        private final String drawReason;

        private AppliedMove(boolean accepted, String errorCode, String message, FenBoard board,
                            AramMatchState aramState, String notation, boolean check, boolean checkmate,
                            boolean draw, String drawReason) {
            this.accepted = accepted;
            this.errorCode = errorCode;
            this.message = message;
            this.board = board;
            this.aramState = aramState;
            this.notation = notation;
            this.check = check;
            this.checkmate = checkmate;
            this.draw = draw;
            this.drawReason = drawReason;
        }

        static AppliedMove rejected(String code, String message) {
            return new AppliedMove(false, code, message, null, null, null, false, false, false, null);
        }

        static AppliedMove accepted(FenBoard board, AramMatchState state, String notation,
                                    boolean check, boolean checkmate, boolean draw, String drawReason) {
            return new AppliedMove(true, null, null, board, state, notation, check, checkmate, draw, drawReason);
        }
    }
}
