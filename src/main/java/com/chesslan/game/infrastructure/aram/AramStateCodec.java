package com.chesslan.game.infrastructure.aram;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AramStateCodec {
    private final JsonMapper jsonMapper;

    public String encode(AramMatchState state) {
        if (state == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(state);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to serialize ARAM state");
        }
    }

    public AramMatchState decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, AramMatchState.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Stored ARAM state is invalid");
        }
    }
}
