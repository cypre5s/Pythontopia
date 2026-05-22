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
import com.pythontopia.world.crop.CropStateService;
import com.pythontopia.world.currency.CurrencyRepository;
import com.pythontopia.world.currency.dto.PlayerCurrencyDto;
import com.pythontopia.world.dto.GameBootstrapResponse;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.inventory.InventoryRepository;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
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
 * Assembles {@link GameBootstrapResponse} in a single transaction so the front-end pixel town can
 * hydrate after login with one round-trip. Lazily provisions the world session + player + currency
 * rows when they are missing so freshly-registered learners always get a non-null payload.
 */
@Service
public class GameBootstrapService {

  private static final long DEFAULT_PACK_ID = 43L;

  private final GameBootstrapRepository bootstrapRepository;
  private final WorldRepository worldRepository;
  private final ProfileRepository profileRepository;
  private final CropStateService cropStateService;
  private final QuestService questService;
  private final MasteryService masteryService;
  private final LearningPathRepository pathRepository;
  private final InventoryRepository inventoryRepository;
  private final SkillRepository skillRepository;
  private final CurrencyRepository currencyRepository;
  private final NpcDialogStateRepository npcDialogStateRepository;
  private final SaveSlotRepository saveSlotRepository;
  private final CalendarService calendarService;

  public GameBootstrapService(
      GameBootstrapRepository bootstrapRepository,
      WorldRepository worldRepository,
      ProfileRepository profileRepository,
      CropStateService cropStateService,
      QuestService questService,
      MasteryService masteryService,
      LearningPathRepository pathRepository,
      InventoryRepository inventoryRepository,
      SkillRepository skillRepository,
      CurrencyRepository currencyRepository,
      NpcDialogStateRepository npcDialogStateRepository,
      SaveSlotRepository saveSlotRepository,
      CalendarService calendarService) {
    this.bootstrapRepository = bootstrapRepository;
    this.worldRepository = worldRepository;
    this.profileRepository = profileRepository;
    this.cropStateService = cropStateService;
    this.questService = questService;
    this.masteryService = masteryService;
    this.pathRepository = pathRepository;
    this.inventoryRepository = inventoryRepository;
    this.skillRepository = skillRepository;
    this.currencyRepository = currencyRepository;
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
    currencyRepository.findOrInit(userId);

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
    var crops = cropStateService.getMap(userId);
    List<QuestDto> quests = questService.today(userId);
    var mastery = masteryService.listSnapshots(userId);
    List<com.pythontopia.learner.path.dto.LearningPathNodeDto> pathNodes =
        pathRepository
            .findActive(userId, DEFAULT_PACK_ID)
            .map(LearningPathDto::nodes)
            .orElse(List.of());
    List<InventoryItemDto> inventory =
        inventoryRepository.findInventory(userId).stream().map(InventoryItemDto::from).toList();
    List<LearnerSkillDto> skills =
        skillRepository.findUnlocked(userId).stream().map(LearnerSkillDto::from).toList();
    PlayerCurrencyDto currency =
        PlayerCurrencyDto.from(currencyRepository.findOrInit(userId));
    List<NpcDialogStateDto> npcStates =
        npcDialogStateRepository.findAll(userId).stream().map(NpcDialogStateDto::from).toList();
    SaveSlotDto activeSlot =
        saveSlotRepository.findActive(userId).map(SaveSlotDto::from).orElse(null);
    var pendingEvents = calendarService.pendingEvents(userId);

    return new GameBootstrapResponse(
        world,
        player,
        profile,
        crops,
        quests,
        mastery,
        pathNodes,
        inventory,
        skills,
        currency,
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
