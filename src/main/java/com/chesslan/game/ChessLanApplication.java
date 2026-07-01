package com.chesslan.game;

import com.chesslan.game.common.config.RenderEnvironmentInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChessLanApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ChessLanApplication.class);
        application.addInitializers(new RenderEnvironmentInitializer());
        application.run(args);
    }
}
