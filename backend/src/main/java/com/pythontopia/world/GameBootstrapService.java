package com.pythontopia.world;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.mastery.MasteryService;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.repository.LearningPathRepository;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.service.QuestService;
import com.pythontopia.world.calendar.CalendarService;
import com.pythontopia.world.dto.GameBootstrapResponse;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.npc.NpcDialogStateRepository;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.repository.GameBootstrapRepository;
import com.pythontopia.world.repository.WorldRepository;
import com.pythontopia.world.save.SaveSlotRepository;
import com.pythontopia.world.save.dto.SaveSlotDto;
import com.pythontopia.world.skill.SkillRepository;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在单个事务内组装 {@link GameBootstrapResponse}，让前端像素小镇登录后一次往返即可水合。当 world session
 * 与 player 行缺失时惰性创建，保证新注册学习者总能拿到非空载荷。
 */
@Service
public class GameBootstrapService {

  private static final long DEFAULT_PACK_ID = 43L;

  private final GameBootstrapRepository bootstrapRepository;
  private final WorldRepository worldRepository;
  private final ProfileRepository profileRepository;
  private final QuestService questService;
  private final MasteryService masteryService;
  private final LearningPathRepository pathRepository;
  private final SkillRepository skillRepository;
  private final NpcDialogStateRepository npcDialogStateRepository;
  private final SaveSlotRepository saveSlotRepository;
  private final CalendarService calendarService;

  public GameBootstrapService(
      GameBootstrapRepository bootstrapRepository,
      WorldRepository worldRepository,
      ProfileRepository profileRepository,
      QuestService questService,
      MasteryService masteryService,
      LearningPathRepository pathRepository,
      SkillRepository skillRepository,
      NpcDialogStateRepository npcDialogStateRepository,
      SaveSlotRepository saveSlotRepository,
      CalendarService calendarService) {
    this.bootstrapRepository = bootstrapRepository;
    this.worldRepository = worldRepository;
    this.profileRepository = profileRepository;
    this.questService = questService;
    this.masteryService = masteryService;
    this.pathRepository = pathRepository;
    this.skillRepository = skillRepository;
    this.npcDialogStateRepository = npcDialogStateRepository;
    this.saveSlotRepository = saveSlotRepository;
    this.calendarService = calendarService;
  }

  @Transactional
  public GameBootstrapResponse bootstrap(Long userId) {
    if (userId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "BOOTSTRAP_USER_REQUIRED", "userId is required");
    }
    ensureWorldSession(userId);
    ensurePlayer(userId);

    GameBootstrapRepository.BootstrapRow row =
        bootstrapRepository
            .findRow(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BOOTSTRAP_VIEW_EMPTY",
                        "game_bootstrap_view returned no row after lazy provisioning"));
    WorldSnapshotDto world = row.world();
    PlayerStateDto player = row.player();

    LearnerProfileDto profile = profileRepository.findProfile(userId).orElse(null);
    List<QuestDto> quests = questService.today(userId);
    var mastery = masteryService.listSnapshots(userId);
    List<com.pythontopia.learner.path.dto.LearningPathNodeDto> pathNodes =
        pathRepository
            .findActive(userId, DEFAULT_PACK_ID)
            .map(LearningPathDto::nodes)
            .orElse(List.of());
    List<LearnerSkillDto> skills =
        skillRepository.findUnlocked(userId).stream().map(LearnerSkillDto::from).toList();
    List<NpcDialogStateDto> npcStates =
        npcDialogStateRepository.findAll(userId).stream().map(NpcDialogStateDto::from).toList();
    SaveSlotDto activeSlot =
        saveSlotRepository.findActive(userId).map(SaveSlotDto::from).orElse(null);
    var pendingEvents = calendarService.pendingEvents(userId);

    return new GameBootstrapResponse(
        world,
        player,
        profile,
        quests,
        mastery,
        pathNodes,
        skills,
        npcStates,
        activeSlot,
        pendingEvents);
  }

  private void ensureWorldSession(Long userId) {
    if (worldRepository.findSession(userId).isEmpty()) {
      worldRepository.createSession(userId);
    }
  }

  private void ensurePlayer(Long userId) {
    if (worldRepository.findPlayer(userId).isEmpty()) {
      worldRepository.createPlayer(userId);
    }
  }
}
