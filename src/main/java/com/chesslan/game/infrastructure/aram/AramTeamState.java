package com.chesslan.game.infrastructure.aram;

import java.util.List;

public record AramTeamState(
        String team,
        AramBuffId buff,
        List<String> commandantPawns,
        String swappedKnight,
        String swappedBishop,
        String originalQueen,
        boolean suicideBomberUsed,
        int queenTeleportUses,
        int queenTeleportCooldown
) {
    public AramTeamState {
        commandantPawns = commandantPawns == null ? List.of() : List.copyOf(commandantPawns);
        swappedKnight = swappedKnight == null ? "" : swappedKnight;
        swappedBishop = swappedBishop == null ? "" : swappedBishop;
        originalQueen = originalQueen == null ? "" : originalQueen;
        queenTeleportUses = Math.max(0, Math.min(5, queenTeleportUses));
        queenTeleportCooldown = Math.max(0, queenTeleportCooldown);
    }
}
