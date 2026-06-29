package com.pythontopia.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pythontopia.learner.mastery.MasteryService;
import com.pythontopia.learner.path.repository.LearningPathRepository;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.world.calendar.CalendarService;
import com.pythontopia.world.domain.PlayerState;
import com.pythontopia.world.domain.WorldSession;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.npc.NpcDialogState;
import com.pythontopia.world.npc.NpcDialogStateRepository;
import com.pythontopia.world.repository.GameBootstrapRepository;
import com.pythontopia.world.repository.WorldRepository;
import com.pythontopia.world.save.SaveSlotRepository;
import com.pythontopia.world.skill.SkillRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameBootstrapServiceTest {

  private final GameBootstrapRepository bootstrapRepository = mock(GameBootstrapRepository.class);
  private final WorldRepository worldRepository = mock(WorldRepository.class);
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final QuestService questService = mock(QuestService.class);
  private final MasteryService masteryService = mock(MasteryService.class);
  private final LearningPathRepository pathRepository = mock(LearningPathRepository.class);
  private final SkillRepository skillRepository = mock(SkillRepository.class);
  private final NpcDialogStateRepository npcDialogStateRepository = mock(NpcDialogStateRepository.class);
  private final SaveSlotRepository saveSlotRepository = mock(SaveSlotRepository.class);
  private final CalendarService calendarService = mock(CalendarService.class);

  private final GameBootstrapService service =
      new GameBootstrapService(
          bootstrapRepository,
          worldRepository,
          profileRepository,
          questService,
          masteryService,
          pathRepository,
          skillRepository,
          npcDialogStateRepository,
          saveSlotRepository,
          calendarService);

  @Test
  void bootstrapAlwaysReturnsProvisionedProfile() {
    long userId = 7L;
    OffsetDateTime now = OffsetDateTime.parse("2026-06-29T10:00:00Z");
    var profile =
        new LearnerProfileDto(
            91L, userId, 43L, null, null, null, null, null, null, null, null, null);

    when(worldRepository.findSession(userId))
        .thenReturn(Optional.of(new WorldSession(11L, userId, 1, "SUNNY", 480)));
    when(worldRepository.findPlayer(userId))
        .thenReturn(Optional.of(new PlayerState(12L, userId, "student-default", "town_hub", 12, 10, "DOWN")));
    when(bootstrapRepository.findRow(userId))
        .thenReturn(
            Optional.of(
                new GameBootstrapRepository.BootstrapRow(
                    new WorldSnapshotDto(
                        11L, 1, 1, "monday", "spring", "morning", "SUNNY", 480, now, now),
                    new PlayerStateDto(12L, "student-default", "town_hub", 12, 10, "DOWN"))));
    when(profileRepository.ensureDefaultProfile(userId)).thenReturn(profile);
    when(questService.today(userId)).thenReturn(List.of());
    when(masteryService.listSnapshots(userId)).thenReturn(List.of());
    when(pathRepository.findActive(userId, 43L)).thenReturn(Optional.empty());
    when(skillRepository.findUnlocked(userId)).thenReturn(List.of());
    when(npcDialogStateRepository.findAllOrInitTownHub(userId))
        .thenReturn(
            List.of(
                new NpcDialogState(
                    31L,
                    userId,
                    "mayor_py",
                    null,
                    List.of(),
                    null,
                    JsonNodeFactory.instance.objectNode(),
                    now,
                    now)));
    when(saveSlotRepository.findActive(userId)).thenReturn(Optional.empty());
    when(calendarService.pendingEvents(userId)).thenReturn(List.of());

    var response = service.bootstrap(userId);

    assertThat(response.profile()).isSameAs(profile);
    assertThat(response.player().mapKey()).isEqualTo("town_hub");
    assertThat(response.npcStates()).extracting("npcKey").containsExactly("mayor_py");
    verify(profileRepository).ensureDefaultProfile(userId);
    verify(npcDialogStateRepository).findAllOrInitTownHub(userId);
  }
}
