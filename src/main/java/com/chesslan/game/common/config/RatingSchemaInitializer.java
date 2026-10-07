package com.chesslan.game.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatingSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        // Hibernate update adds fields but does not know whether old stats were already awarded.
        // Keep settled legacy records read-only, and never re-apply their totals on retries.
        jdbc.update("update matches set statistics_processed = true where status <> 'ACTIVE' "
                + "and settlement_decision = 'LEGACY' and statistics_processed = false");
    }
}
