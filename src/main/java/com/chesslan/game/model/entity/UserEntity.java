package com.chesslan.game.model.entity;

import com.chesslan.game.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity implements UserDetails {
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Integer elo = 1200;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(nullable = false)
    private Long exp = 0L;

    @Column(nullable = false)
    private Long gold = 0L;

    @Column(nullable = false)
    private Long totalMatches = 0L;

    @Column(nullable = false)
    private Long totalWins = 0L;

    @Column(nullable = false)
    private Long totalLosses = 0L;

    @Column(nullable = false)
    private Long totalDraws = 0L;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("PLAYER"));
    }
}
