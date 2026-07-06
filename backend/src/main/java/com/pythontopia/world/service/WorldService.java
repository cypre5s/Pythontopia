package com.pythontopia.world.service;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.domain.PlayerState;
import com.pythontopia.world.domain.WorldSession;
import com.pythontopia.world.dto.NpcStateDto;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.PlayerStatePatch;
import com.pythontopia.world.dto.WorldSessionDto;
import com.pythontopia.world.repository.WorldRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WorldService {

  private final WorldRepository repository;

  public WorldService(WorldRepository repository) {
    this.repository = repository;
  }

  public WorldSessionDto getOrCreateSession(CurrentUserPrincipal user) {
    requireStudent(user);
    WorldSession session = repository.findSession(user.userId()).orElseGet(() -> repository.createSession(user.userId()));
    PlayerState player = repository.findPlayer(user.userId()).orElseGet(() -> repository.createPlayer(user.userId()));
    return toDto(session, player);
  }

  public PlayerStateDto patchPlayer(CurrentUserPrincipal user, PlayerStatePatch patch) {
    requireStudent(user);
    PlayerState player =
        repository.updatePlayer(user.userId(), patch.tileX(), patch.tileY(), patch.direction());
    return toDto(player);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT && user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only students or admins have world state");
    }
  }

  private WorldSessionDto toDto(WorldSession session, PlayerState player) {
    return new WorldSessionDto(
        session.id(),
        session.dayIndex(),
        session.weather(),
        session.inGameMinutes(),
        toDto(player),
        List.of(new NpcStateDto("mayor_py", "NEUTRAL")));
  }

  private PlayerStateDto toDto(PlayerState player) {
    return new PlayerStateDto(
        player.id(),
        player.avatarKey(),
        player.mapKey(),
        player.tileX(),
        player.tileY(),
        player.direction());
  }
}
