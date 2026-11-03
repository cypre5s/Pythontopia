# Reborn HTTP contract

All routes use /api. JSON fields use snake_case. Bearer session authentication is required except auth/register, auth/login and health. Student requests carry explicit classroom_id. Public registration never accepts a teacher/admin role. Errors return detail; malformed inputs return422. All public timestamps include an explicit UTC offset; release inputs must carry a timezone and are normalized to UTC before storage.

## Session and classes

- POST /auth/register {email,password,name}; POST /auth/login {email,password} -> {access_token,user:{id,email,name,role}}. GET /auth/me; POST /auth/logout.
- GET /classrooms; POST /classrooms {name} (teacher); POST /classrooms/join {join_code}; DELETE /teacher/classrooms/{id}/members/{user_id} (owner/admin).
- GET /world?classroom_id -> player position, configured NPCs/scripts, versioned quests and node statuses, available chapters and world flags. PUT /world/position {classroom_id,map_key,tile_x,tile_y,direction}.
- POST /events {classroom_id,event_id,quest_id,node_id,evidence}. Evidence fields are npc_key/dialogue_id/choice/object_id/map_key/tile_x/tile_y/record_id/submission_id as appropriate. The server validates current node, context and learning evidence. Repeated identical IDs are idempotent; different bodies with the same ID return409.
- POST /dialogues/{npc_key}/steps {classroom_id,node_id,choice?} -> {node,next_id,completed_root,effects}. The node returned is the one just completed. PLAY_DIALOGUE events require a real terminal script marker. Whitelisted remember/open_activity effects never grade or unlock a task directly. Normal scripts use0 model calls.

## Learning and formal submissions

- GET /activities/{id}?classroom_id -> versioned activity, authorized material, public questions, knowledge_point_ids and public knowledge_points with source IDs. POST /activities/{id}/participation {classroom_id,page_no?,video_time?} -> record_id. Video sends video_time only; reading records participation, not mastery.
- GET /library?classroom_id lists only bound authorized material versions. GET /materials/{id}/versions/{version}?classroom_id returns a readable version; GET the same path/file streams authorized bytes with Range206 support. Files never live in public uploads.
- POST /submissions {classroom_id,activity_id,question_id,idempotency_key,mode:run|submit,answer?,code?}; GET /submissions/{id}?classroom_id. Return id, activity/question/version/mode/status/feedback/created_at. Run never forms a formal grade.
- Feedback contains detail/status and optional public samples[{status,stdout,stderr,compile_output}]. Hidden tests, expected outputs and hidden indices are absent. States include prepared/dispatching/queued/running/accepted/wrong_answer/compile_error/runtime_error/time_limit/memory_limit/infrastructure_error/submission_unknown. Uncertain dispatches require reconciliation; no blind resubmit.

## Teachers and authoring

- GET /teacher/courses -> {chapters,knowledge_points,activities,npcs}, filtered to shared+owned content (admin may see all).
- POST/PUT/DELETE /teacher/chapters[/id]. Create {title,order_index?}; update {title?,order_index?}. POST/PUT/DELETE /teacher/knowledge-points[/id]. Create {chapter_id,title,content?}; update {title?,content?}. Shared source content is read-only for teachers; owned unreferenced entries may be deleted, otherwise409. Cross-owner reads/edits/bindings are rejected.
- POST /teacher/activities {id,chapter_id,title,kind,npc_key,material_id?,assistant_allowed?,knowledge_point_ids?}; PUT /teacher/activities/{id} updates owned content and creates a new immutable snapshot. KC IDs must be unique positive integers in the same authorized chapter. Shared activities cannot be overwritten.
- GET /teacher/questions; POST /teacher/questions {id,activity_id,title,kind,prompt,options,samples,answer,hidden_cases,source?}. New questions produce a new activity_version; tasks must explicitly reference it. Python samples+hidden cases total<=20 with validated field shapes and bounded text.
- GET /teacher/quests; POST /teacher/quests/validate {definition}; POST /teacher/quests {definition}. Immutable versions; strict schema, graph, content and ownership validation. kind=main|side; optional nodes are booleans; activity_version must identify a real positive-integer snapshot. ALL merges ignore unselected branches but still require the selected branch and subsequent learning. Side quests do not implicitly block main chapter prerequisites.
- PUT /teacher/classrooms/{id}/releases/{chapter_id} {status:published|paused,opens_at:ISO8601|null,prerequisite_chapter_id?,version}. GET /teacher/classrooms/{id}/releases. Server-time evaluation is immediate. Version refers to published task definitions; their nodes pin concrete activity/material/question snapshots.
- GET /teacher/classrooms/{id}/progress; GET /teacher/classrooms/{id}/students/{student_id}: real members, node events, participation records, submissions and error distribution. Teachers own the class; preview cannot write student grades.
- POST /teacher/materials/upload multipart {chapter_id,title,material_id?,file} -> {id,version,status,title}; GET /teacher/materials. Status queued/processing/ready/failed persists. Upload alone does not publish; bind the explicit version through an owned activity. New versions never silently replace old references.
- POST /teacher/npcs is admin-only because world characters are global. Teachers bind existing characters using activities. Script schema supports speaker/portrait/text, next, choices, flag conditions, once and restricted effects; no arbitrary expressions/SQL/code.

## Active teaching assistant

- GET /assistant/status -> available, reason, model_calls. Missing credentials are explicit.
- POST /assistant/sessions {classroom_id,activity_id} -> id; GET /assistant/sessions/{id}; POST /assistant/sessions/{id}/messages {message,page_no?,video_time?,code?} streams SSE meta(request_id/session_id), token(text), references(items), done(status/tokens), error(detail).
- POST /assistant/requests/{id}/cancel; owner validation and thread-safe cancellation. Sessions pin user/class/activity/material versions. Tests prohibit AI on assessments when the server policy forbids it.
- One create_agent factory, five read-only tools, no grading/unlock/publish/SQL/shell tools. Default3 model and5 tool calls,45s timeout,512 output tokens/model, conservative request/user/class budgets. Production has no fake provider.

Paused policy denies new content/progress/assistant requests immediately and preserves history; reopening continues stored state. Built-in tasks are arrival (chapter179) and lost-language (180). Original raw questions except reviewed476 stay archived/unpublished. Every current behavior is also exercised by tests; skipped external validation remains separately documented.
