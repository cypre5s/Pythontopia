package com.pythontopia.world.dto;

import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import com.pythontopia.learner.path.dto.LearningPathNodeDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import com.pythontopia.world.crop.dto.CropTileDto;
import com.pythontopia.world.currency.dto.PlayerCurrencyDto;
import com.pythontopia.world.inventory.dto.InventoryItemDto;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.save.dto.SaveSlotDto;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import java.util.List;

/**
 * Aggregated payload returned by {@code POST /api/game/bootstrap}. Mirrors the {@code
 * game_bootstrap_view} 1:1 fields plus the 1:N collections the front-end pixel town needs to
 * hydrate in one round-trip.
 */
public record GameBootstrapResponse(
    WorldSnapshotDto world,
    PlayerStateDto player,
    LearnerProfileDto profile,
    List<CropTileDto> crops,
    List<QuestDto> quests,
    List<MasterySnapshotDto> mastery,
    List<LearningPathNodeDto> path,
    List<InventoryItemDto> inventory,
    List<LearnerSkillDto> skills,
    PlayerCurrencyDto currency,
    List<NpcDialogStateDto> npcStates,
    SaveSlotDto activeSlot,
    List<WorldEventDto> pendingEvents) {}
