package com.chesslan.game.infrastructure.aram;

public record AramMatchState(
        int version,
        String seed,
        AramTeamState white,
        AramTeamState black
) {
}
