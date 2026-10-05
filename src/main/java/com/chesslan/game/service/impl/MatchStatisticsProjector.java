package com.chesslan.game.service.impl;

import com.chesslan.game.model.dto.match.MatchStatisticsDTO;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchMoveEntity;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Deterministic ledger from persisted moves, never starting-material deficits. */
@Component
public class MatchStatisticsProjector {
    private static final String INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public MatchStatisticsDTO project(MatchEntity match, List<MatchMoveEntity> moves) {
        var entries = new ArrayList<MatchStatisticsDTO.Entry>();
        char[] before = board(INITIAL);
        String beforeFen = INITIAL;
        int expectedNumber = 1;
        boolean complete = true;
        for (var move : moves.stream().sorted(Comparator.comparing(MatchMoveEntity::getMoveNumber)).toList()) {
            if (move.getMoveNumber() != expectedNumber) complete = false;
            String actor = move.getPlayer().getId().equals(match.getWhitePlayer().getId()) ? "WHITE" : "BLACK";
            String id = move.getId() == null ? "move-" + move.getMoveNumber() : move.getId().toString();
            char[] after;
            try { after = board(move.getFenAfter()); }
            catch (IllegalArgumentException ex) { after = before.clone(); complete = false; }
            try {
                if (!complete) throw new IllegalArgumentException("History gap");
                int from = square(move.getFromSquare()), to = square(move.getToSquare());
                char moving = before[from], captured = before[to];
                if (moving == '.') throw new IllegalArgumentException("Missing moving piece");
                if (!side(moving).equalsIgnoreCase(actor)) throw new IllegalArgumentException("Moving piece belongs to another player");
                char[] ordinary = before.clone();
                if (Character.toLowerCase(moving) == 'p' && from % 8 != to % 8 && captured == '.') {
                    String[] fields = beforeFen.split(" ");
                    if (fields.length < 4 || !fields[3].equals(move.getToSquare())) throw new IllegalArgumentException("Missing en passant right");
                    int ep = (from / 8) * 8 + to % 8;
                    captured = before[ep]; ordinary[ep] = '.';
                    if (Character.toLowerCase(captured) != 'p') throw new IllegalArgumentException("Invalid en passant");
                }
                if (captured != '.' && Character.isUpperCase(captured) == Character.isUpperCase(moving))
                    throw new IllegalArgumentException("Unexpected allied capture");
                ordinary[from] = '.'; ordinary[to] = moving;
                if (Character.toLowerCase(moving) == 'k' && Math.abs(to % 8 - from % 8) == 2) {
                    int rookFrom = from / 8 * 8 + (to > from ? 7 : 0), rookTo = from / 8 * 8 + (to > from ? 5 : 3);
                    ordinary[rookTo] = ordinary[rookFrom]; ordinary[rookFrom] = '.';
                }
                if (Character.toLowerCase(moving) == 'p' && (to / 8 == 0 || to / 8 == 7)) ordinary[to] = after[to];
                entries.add(new MatchStatisticsDTO.Entry(id, move.getMoveNumber(), actor, move.getNotation(), true, kind(captured)));
                for (int i = 0; i < 64; i++) {
                    if (ordinary[i] == after[i]) continue;
                    String text = after[i] == '.' ? side(ordinary[i]) + " " + kind(ordinary[i]) + " removed at " + name(i)
                            : "Board effect at " + name(i) + ": " + side(after[i]) + " " + kind(after[i]);
                    entries.add(new MatchStatisticsDTO.Entry(id + "-effect-" + i, move.getMoveNumber(), actor, text, false, null));
                }
            } catch (IllegalArgumentException ex) {
                complete = false;
                entries.add(new MatchStatisticsDTO.Entry(id, move.getMoveNumber(), actor, move.getNotation(), true, null));
            }
            before = after; beforeFen = move.getFenAfter(); expectedNumber = move.getMoveNumber() + 1;
        }
        complete &= expectedNumber - 1 == match.getMoveCount();
        try { complete &= Arrays.equals(before, board(match.getCurrentFen())); }
        catch (IllegalArgumentException ex) { complete = false; }
        LocalDateTime end = match.getFinishedAt() == null ? LocalDateTime.now() : match.getFinishedAt();
        long elapsed = match.getStartedAt() == null ? 0 : Math.max(0, Duration.between(match.getStartedAt(), end).getSeconds());
        return new MatchStatisticsDTO(1, match.getMoveCount(), elapsed, complete, complete, List.copyOf(entries));
    }
    static char[] board(String fen) {
        if (fen == null) throw new IllegalArgumentException("Missing FEN");
        String[] rows = fen.split(" ")[0].split("/");
        if (rows.length != 8) throw new IllegalArgumentException("Invalid FEN");
        char[] squares = new char[64]; Arrays.fill(squares, '.');
        for (int r = 0; r < 8; r++) {
            int file = 0;
            for (char c : rows[r].toCharArray()) {
                if (c >= '1' && c <= '8') file += c - '0';
                else {
                    if (file >= 8 || "prnbqkPRNBQK".indexOf(c) < 0) throw new IllegalArgumentException("Invalid piece");
                    squares[(7 - r) * 8 + file++] = c;
                }
            }
            if (file != 8) throw new IllegalArgumentException("Invalid rank");
        }
        return squares;
    }
    private static int square(String value) {
        if (value == null || !value.matches("[a-h][1-8]")) throw new IllegalArgumentException("Invalid square");
        return (value.charAt(1) - '1') * 8 + value.charAt(0) - 'a';
    }
    private static String name(int square) { return "" + (char)('a' + square % 8) + (1 + square / 8); }
    private static String side(char piece) { return Character.isUpperCase(piece) ? "White" : "Black"; }
    private static String kind(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> "Pawn"; case 'n' -> "Knight"; case 'b' -> "Bishop";
            case 'r' -> "Rook"; case 'q' -> "Queen"; case 'k' -> "King"; default -> null;
        };
    }
}
