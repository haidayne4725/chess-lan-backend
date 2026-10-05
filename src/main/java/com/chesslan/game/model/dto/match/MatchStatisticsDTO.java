package com.chesslan.game.model.dto.match;
import java.util.List;

/** Public board events only; no private ARAM selections or cooldowns. */
public record MatchStatisticsDTO(int version, int moveCount, long elapsedSeconds,
        boolean historyComplete, boolean capturesComplete, List<Entry> entries) {
    public record Entry(String id, int moveNumber, String actor, String text,
                        boolean isMove, String captured) { }
}
