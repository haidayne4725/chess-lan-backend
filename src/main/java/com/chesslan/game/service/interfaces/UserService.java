package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.user.UserProfileResponseDTO;

public interface UserService {
    UserProfileResponseDTO me(String username);
}
