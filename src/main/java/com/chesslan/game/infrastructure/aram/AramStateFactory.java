package com.chesslan.game.infrastructure.aram;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AramStateFactory {
    private static final AramBuffId[] BUFFS = AramBuffId.values();

    public AramMatchState create(String seed) {
        String safeSeed = seed == null ? "" : seed;
        return new AramMatchState(
                1,
                safeSeed,
                createTeam("WHITE", safeSeed, 1, 2),
                createTeam("BLACK", safeSeed, 8, 7)
        );
    }

    private AramTeamState createTeam(String team, String seed, int homeRank, int pawnRank) {
        long hash = Integer.toUnsignedLong(stableHash(seed + "|" + team + "|ARAM-V1"));
        AramBuffId buff = BUFFS[(int) (hash % BUFFS.length)];
        List<String> commandants = buff == AramBuffId.CommandantPawn
                ? List.of("d" + pawnRank, "e" + pawnRank, "c" + pawnRank)
                : List.of();
        String knight = "";
        String bishop = "";
        if (buff == AramBuffId.Doppelganger) {
            String[] knights = {"b" + homeRank, "g" + homeRank};
            String[] bishops = {"c" + homeRank, "f" + homeRank};
            knight = knights[(int) (hash % knights.length)];
            bishop = bishops[(int) ((hash >>> 8) % bishops.length)];
        }
        return new AramTeamState(
                team,
                buff,
                commandants,
                knight,
                bishop,
                "d" + homeRank,
                false,
                0,
                0
        );
    }

    static int stableHash(String value) {
        int hash = 0x811c9dc5;
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }
}
