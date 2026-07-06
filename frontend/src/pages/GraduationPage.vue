<script setup lang="ts">
// 毕业典礼页（主线终局演出）。
// R-15：以「7 章全部 ≥1★（全部通过章末测试）」为毕业条件，星级取后端权威 mainline overview；
// 旧 mainQuest「击败第 7 章 BOSS」判定已退役。
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useMainlineStore } from '@stores/mainline';
import { useLearningStore } from '@stores/learning';
import { MAIN_QUEST_BOSSES } from '@/data/bossCatalog';
import { celebrateLevelUp } from '@/utils/celebrate';
import PyButton from '@/components/ui/PyButton.vue';

const router = useRouter();
const mainline = useMainlineStore();
const learning = useLearningStore();

// 终章故事一句话：复用第 7 章 boss 的 storyboardCue 作为毕业致辞文案。
const finalBoss = MAIN_QUEST_BOSSES.find((b) => b.chapter === 7) ?? null;

const totalStars = computed<number>(() => mainline.totalStars);
const clearedChapters = computed<number>(
  () => mainline.chapters.filter((c) => c.stars >= 1).length,
);
// 毕业条件：7 章全部 ≥1★（全部通过章末测试）。
const graduated = computed<boolean>(
  () => mainline.chapters.length >= 7 && mainline.chapters.every((c) => c.stars >= 1),
);
const masteredKcCount = computed<number>(
  () => learning.field.masterySnapshot.filter((entry) => entry.masteryScore >= 0.4).length,
);

onMounted(async () => {
  // 终局演出依赖后端权威星级：先拉总览，未达毕业条件回小镇（防经 URL 提前进入）。
  await mainline.load();
  if (!graduated.value) {
    void router.replace('/world');
    return;
  }
  try {
    celebrateLevelUp();
  } catch {
    /* 庆祝特效失败不影响页面 */
  }
});

function backToWorld(): void {
  void router.push('/world');
}

function reviewMastery(): void {
  void router.push('/profile-room');
}
</script>

<template>
  <section
    v-if="graduated"
    class="graduation"
    data-testid="graduation-page"
  >
    <div class="graduation__card">
      <p class="graduation__badge">
        🎓
      </p>
      <h1 class="graduation__title">
        Pythontopia 毕业典礼
      </h1>
      <p class="graduation__cue">
        {{ finalBoss?.storyboardCue ?? '镇长 Py 在毕业典礼把镇徽别在你身上。' }}
      </p>

      <dl class="graduation__stats">
        <div class="stat">
          <dt>总星数</dt>
          <dd data-testid="graduation-stars">
            {{ totalStars }} / 21
          </dd>
        </div>
        <div class="stat">
          <dt>通关章节</dt>
          <dd data-testid="graduation-chapters">
            {{ clearedChapters }} / 7
          </dd>
        </div>
        <div class="stat">
          <dt>达标知识点</dt>
          <dd data-testid="graduation-kcs">
            {{ masteredKcCount }} / 65
          </dd>
        </div>
      </dl>

      <p class="graduation__titles">
        获得：<span class="tag">毕业袍</span><span class="tag">称号 · Pythontopia 校友</span>
      </p>

      <div class="graduation__actions">
        <PyButton
          variant="primary"
          test-id="graduation-back"
          @click="backToWorld"
        >
          回到小镇
        </PyButton>
        <PyButton
          variant="ghost"
          test-id="graduation-review"
          @click="reviewMastery"
        >
          回顾我的学习档案
        </PyButton>
      </div>
    </div>
  </section>
</template>

<style scoped>
.graduation {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 20%, rgba(255, 214, 102, 0.18), transparent 60%),
    linear-gradient(180deg, #131a2a 0%, #1c1810 100%);
}

.graduation__card {
  max-width: 560px;
  width: 100%;
  text-align: center;
  padding: 36px 32px;
  border: 2px solid #b08d3f;
  border-radius: 12px;
  background: rgba(20, 16, 10, 0.92);
  color: #fff8e1;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.5);
}

.graduation__badge {
  font-size: 56px;
  margin: 0 0 8px;
}

.graduation__title {
  margin: 0 0 12px;
  font-size: 26px;
  letter-spacing: 2px;
}

.graduation__cue {
  margin: 0 0 24px;
  color: #ffd666;
  line-height: 1.7;
}

.graduation__stats {
  display: flex;
  justify-content: center;
  gap: 28px;
  margin: 0 0 20px;
}

.stat dt {
  font-size: 12px;
  color: #c9b98a;
}

.stat dd {
  margin: 4px 0 0;
  font-size: 22px;
  font-weight: 700;
}

.graduation__titles {
  margin: 0 0 24px;
  font-size: 14px;
}

.tag {
  display: inline-block;
  margin: 0 4px;
  padding: 2px 10px;
  border: 1px solid #b08d3f;
  border-radius: 999px;
  font-size: 12px;
  color: #ffd666;
}

.graduation__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
