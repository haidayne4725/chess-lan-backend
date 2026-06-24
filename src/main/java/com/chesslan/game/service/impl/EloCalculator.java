package com.chesslan.game.service.impl;

import org.springframework.stereotype.Component;

@Component
public class EloCalculator {
    private static final int K_FACTOR = 32;

    public RatingChange calculate(int whiteRating, int blackRating, double whiteScore) {
        double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (blackRating - whiteRating) / 400.0));
        double expectedBlack = 1.0 - expectedWhite;
        int newWhite = (int) Math.round(whiteRating + K_FACTOR * (whiteScore - expectedWhite));
        int newBlack = (int) Math.round(blackRating + K_FACTOR * ((1.0 - whiteScore) - expectedBlack));
        return new RatingChange(newWhite, newBlack);
    }

    public record RatingChange(int whiteRating, int blackRating) {
    }
}
