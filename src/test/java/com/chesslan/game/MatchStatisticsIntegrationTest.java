package com.chesslan.game;

import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.model.dto.match.MatchStatisticsDTO;
import com.chesslan.game.service.interfaces.AuthService;
import com.chesslan.game.service.interfaces.RoomService;
import com.chesslan.game.service.interfaces.MatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class MatchStatisticsIntegrationTest {
    @Autowired AuthService auth;
    @Autowired RoomService rooms;
    @Autowired MatchService matches;
    @Test void captureReconnectDuplicateAndTerminalUseOnePublicLedger() {
        String suffix=UUID.randomUUID().toString().substring(0,8),white="sw"+suffix,black="sb"+suffix,outsider="so"+suffix;
        auth.signup(new SignupRequestDTO(white,"123456"));auth.signup(new SignupRequestDTO(black,"123456"));auth.signup(new SignupRequestDTO(outsider,"123456"));
        String room=rooms.create(white).roomCode();rooms.join(black,room);
        assertThat(((MatchStatisticsDTO)matches.startMatch(room).get("statistics")).entries()).isEmpty();
        matches.submitMove(white,room,"s1","e2","e4",null);
        matches.submitMove(black,room,"s2","d7","d5",null);
        var result=matches.submitMove(white,room,"s3","e4","d5",null);
        var snapshot=(MatchStatisticsDTO)result.get("statistics");
        assertThat(snapshot.version()).isEqualTo(1);assertThat(snapshot.moveCount()).isEqualTo(3);
        assertThat(snapshot.entries()).hasSize(3);assertThat(snapshot.entries().get(2).captured()).isEqualTo("Pawn");
        assertThat(snapshot.entries().get(2).actor()).isEqualTo("WHITE");assertThat(snapshot.historyComplete()).isTrue();
        assertThat(snapshot.capturesComplete()).isTrue();
        var duplicate=(MatchStatisticsDTO)matches.submitMove(white,room,"s3","e4","d5",null).get("statistics");
        assertThat(duplicate.entries()).isEqualTo(snapshot.entries());
        assertThat(((MatchStatisticsDTO)matches.syncState(black,room).get("statistics")).entries()).isEqualTo(snapshot.entries());
        assertThat(matches.active(white).statistics().entries()).isEqualTo(snapshot.entries());
        assertThatThrownBy(()->matches.syncState(outsider,room)).isInstanceOf(RuntimeException.class);
        var ended=(MatchStatisticsDTO)matches.resign(black,room).get("statistics");
        assertThat(((MatchStatisticsDTO)matches.syncState(white,room).get("statistics")).elapsedSeconds()).isEqualTo(ended.elapsedSeconds());
    }
    @Test void aramRecoveryContainsPublicStatisticsButNoBuffFields() {
        String suffix=UUID.randomUUID().toString().substring(0,8),white="aw"+suffix,black="ab"+suffix;
        auth.signup(new SignupRequestDTO(white,"123456"));auth.signup(new SignupRequestDTO(black,"123456"));
        String room=rooms.create(white,"ARAM").roomCode();rooms.join(black,room,"ARAM");matches.startMatch(room,"ARAM");
        var result=matches.submitMove(white,room,"a1","e2","e4",null,"ARAM");
        var snapshot=(MatchStatisticsDTO)result.get("statistics");
        assertThat(snapshot.entries()).hasSize(1);assertThat(snapshot.entries().get(0).actor()).isEqualTo("WHITE");
        assertThat(snapshot.entries().toString()).doesNotContain("cooldown","buff","seed");
        assertThat(((MatchStatisticsDTO)matches.syncState(black,room,"ARAM").get("statistics")).entries()).isEqualTo(snapshot.entries());
    }
}
