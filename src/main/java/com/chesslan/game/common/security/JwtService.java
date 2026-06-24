package com.chesslan.game.common.security;

import com.chesslan.game.model.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("Missing required property: jwt.secret");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(properties.secret()));
    }

    public String generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claims(Map.of(
                        "uid", user.getId().toString(),
                        "role", "PLAYER",
                        "typ", "access"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenExpMinutes(), ChronoUnit.MINUTES)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpMinutes() * 60;
    }

}
