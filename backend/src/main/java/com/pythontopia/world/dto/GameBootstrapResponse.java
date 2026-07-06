package com.pythontopia.world.dto;

import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import com.pythontopia.learner.path.dto.LearningPathNodeDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.save.dto.SaveSlotDto;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import java.util.List;

/**
 * {@code POST /api/game/bootstrap} 返回的聚合载荷：与 {@code game_bootstrap_view} 视图字段 1:1 对应，
 * 另含前端像素小镇一次性加载所需的 1:N 集合。
 */
public record GameBootstrapResponse(
    WorldSnapshotDto world,
    PlayerStateDto player,
    LearnerProfileDto profile,
    List<QuestDto> quests,
    List<MasterySnapshotDto> mastery,
    List<LearningPathNodeDto> path,
    List<LearnerSkillDto> skills,
    List<NpcDialogStateDto> npcStates,
    SaveSlotDto activeSlot,
    List<WorldEventDto> pendingEvents) {}
