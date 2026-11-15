import { computed, ref, watch } from "vue";
import type { Ref } from "vue";
import type { Player, User, World } from "../types";

export function createWorldPosition(
  user: Ref<User | null>,
  classroomId: Ref<number | null>,
  world: Ref<World | null>,
) {
  const livePlayer = ref<Player | null>(null);
  const positionGeneration = ref(0),
    savedPositionGeneration = ref(-1);
  const mapPosition = computed(() => {
    if (positionGeneration.value !== savedPositionGeneration.value) return null;
    const saved = world.value?.player;
    return saved
      ? livePlayer.value?.map_key === saved.map_key
        ? livePlayer.value
        : saved
      : null;
  });
  function setMapPosition(player: Player) {
    if (
      !user.value ||
      !classroomId.value ||
      positionGeneration.value !== savedPositionGeneration.value ||
      player.map_key !== world.value?.player.map_key ||
      !Number.isFinite(player.tile_x) ||
      !Number.isFinite(player.tile_y)
    )
      return;
    livePlayer.value = { ...player };
  }
  function clearMapPosition() {
    livePlayer.value = null;
  }

  watch(
    [() => user.value?.id, classroomId],
    () => {
      positionGeneration.value++;
      clearMapPosition();
    },
    { flush: "sync" },
  );
  watch(
    world,
    (value) => {
      savedPositionGeneration.value = value ? positionGeneration.value : -1;
    },
    { flush: "sync" },
  );
  watch(
    () => world.value?.player.map_key,
    (map) => {
      if (livePlayer.value?.map_key !== map) clearMapPosition();
    },
    { flush: "sync" },
  );

  return { mapPosition, setMapPosition, clearMapPosition };
}
