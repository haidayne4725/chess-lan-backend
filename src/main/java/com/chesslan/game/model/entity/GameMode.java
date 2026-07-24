package com.chesslan.game.model.entity;

import java.util.Locale;

public enum GameMode {
    CLASSIC,
    ARAM;

    public static GameMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return CLASSIC;
        }
        return GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
