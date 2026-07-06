# Learner Profile Schema

Pythontopia 的学生画像固定绑定 `packId=43` 和课程 `Python语言基础`。画像由访谈、练习、调试、资源反馈、任务和班级表现持续更新；不得只根据一次问卷做静态推荐。

## 8 Dimensions

1. `major`
   - `CS`
   - `NON_CS_STEM`
   - `NON_CS_HUMANITIES`
   - `OTHER`

2. `base_level`
   - `programming_years`: integer, `0..20`
   - `languages_known`: string array
   - `python_self_rated_level`: integer, `1..5`

3. `learning_goal`
   - `kind`: `EXAM` | `PROJECT` | `INTEREST` | `JOB`
   - `deadline_iso`: ISO date string or `null`
   - `target_kc_codes`: KC codes from course pack 43 only

4. `cognitive_style`
   - `field_dependent`: number, `0..1`
   - `sequential_vs_holistic`: number, `-1..1`

5. `learning_preference`
   - `media_priority`: ordered list of `video` | `text` | `mindmap` | `code`
   - `session_minutes_per_day`: integer, `5..240`

6. `error_patterns`
   - `frequent_kc_codes`: KC codes from course pack 43 only
   - `common_categories`: string array, for example `syntax`, `loop`, `type`, `debugging`

7. `practice_capability`
   - `avg_attempts_per_problem`: number, `0..20`
   - `success_rate`: number, `0..1`

8. `emotion_persistence`
   - `resilience`: number, `0..1`
   - `recent_mood`: `positive` | `neutral` | `negative`

## Grounding Rules

- `target_kc_codes` and `frequent_kc_codes` must exist in course pack 43.
- If a value comes from a student statement, the agent must keep an evidence anchor to the interview turn.
- If a value is inferred, it must use low-confidence language in the narrative summary.
- If grounding fails, the agent asks a follow-up question and must not write `learner_profile`.
