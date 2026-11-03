# Reborn HTTP contract (v1)
All paths have `/api` prefix; JSON uses snake_case. Bearer session token required except register/login/health. Explicit classroom_id required for student content/events/submissions/assistant. Errors: `{detail: string}`. No teacher role accepted in public registration.

- POST /auth/register {email,password,name} -> {access_token,user:{id,email,name,role}}
- POST /auth/login {email,password} -> same; GET /auth/me -> user; POST /auth/logout -> {ok:true}
- GET /classrooms -> [{id,name,teacher_id,join_code?}]; POST /classrooms {name} teacher; POST /classrooms/join {join_code}
- GET /world?classroom_id= -> {player:{map_key,tile_x,tile_y,direction},npcs:[{key,name,map_key,tile_x,tile_y,portrait,dialogue:[{id,text,choices?}]}],quests:[{id,title,chapter_id,version,status,nodes:[{id,type,title,target,activity_id?,dependencies,aggregation,optional,status,choices?}]}],chapters:[{id,title,order_index,available}],flags:[]}
- PUT /world/position {classroom_id,map_key,tile_x,tile_y,direction} -> player
- POST /events {classroom_id,event_id,quest_id,node_id,evidence:{npc_key?,dialogue_id?,choice?,object_id?,map_key?,tile_x?,tile_y?,record_id?,submission_id?}} -> {ok:true,duplicate:boolean,world:world}
- GET /activities/{id}?classroom_id= -> {id,chapter_id,title,kind,version,npc_key,assistant_allowed,material:{id,title,kind,version,status,pages:[{id,page_no,title,text}],media_url?,notice?},questions: public Question[]}
- POST /activities/{id}/participation {classroom_id,page_no?,video_time?} -> {record_id}; participation is engagement, not mastery.
- GET /materials/{id}/versions/{version}?classroom_id= -> material; GET /library?classroom_id= -> [{id,title,chapter_id,kind,version}]
- POST /submissions {classroom_id,activity_id,question_id,idempotency_key,answer?,code?,mode:run|submit} -> Submission; GET /submissions/{id}?classroom_id= -> Submission. Public Question {id,title,kind:single_choice|boolean|fill|python,prompt,options,samples,version}; never answers/tests.
- POST /assistant/sessions {classroom_id,activity_id} -> {id}; POST /assistant/sessions/{id}/messages {message,page_no?,video_time?,code?} -> SSE; GET /assistant/status -> explicit availability/counters. Integration agent finalizes streaming contract.
- GET /teacher/courses -> {chapters,activities,npcs}; GET /teacher/classrooms/{id}/progress -> {members:[{id,name,email,completed_nodes,submissions,records}],events,submissions,error_distribution}
- PUT /teacher/classrooms/{id}/releases/{chapter_id} {status:published|paused,opens_at:ISO8601|null,prerequisite_chapter_id?,version:1} -> release; immediate server time evaluation.
- GET /teacher/quests -> definitions; POST /teacher/quests/validate {definition} -> validated; POST /teacher/quests {definition} -> persisted immutable version.
- PUT /teacher/activities/{id} {title?,npc_key?,assistant_allowed?,material_id?} -> activity (new version).
- GET /teacher/questions -> private teacher question list; POST /teacher/questions {id,title,kind,prompt,chapter_id,options,samples,answer,hidden_cases,activity_id} -> id.
- Material upload/list/file routes owned by storage adapter integration; frontend must use authorized blob fetch, no public upload paths.

World NPCs receive server validated configuration, scripts deterministic. Seed classroom is not automatically created. CLI provision-user creates teachers explicitly. Register students; teacher creates class then publishes chapters. Two built-in quest ids: arrival (chapter179), lost-language (chapter180). Event UUID idempotency belongs to user + classroom; effects committed once.


Deterministic script API: `POST /dialogues/{npc_key}/steps {classroom_id,node_id,choice?}` validates order/proximity and returns `{node,next_id,completed_root,effects}`. `node` is the node just completed, `next_id` locates the next script node in world NPC data. Only terminal steps return completed_root. PLAY_DIALOGUE task events require this completed marker. All normal dialogue calls make zero model requests. Schema allows bounded next/choices, `{flag,equals}` conditions, once markers, and remember/open_activity effects; no grade/progress effects.

Only administrators may `POST /teacher/npcs` because world characters are shared globally. Teachers bind existing characters through their own activities. `POST /teacher/activities` creates immutable activity version 1; adding a question generates a new activity version and returns `activity_version`, which must be referenced by the new quest definition/release. Shared seed activities cannot be overwritten by another teacher. `GET /teacher/classrooms/{id}/releases` lists actual release policies. `GET /teacher/classrooms/{id}/students/{student_id}` retrieves that member's versioned learning records and submissions; cross-class access is rejected.

Paused release policy is immediate denial of all new content/progress/assistant requests while historical grades and teacher records remain available. Bound material versions remain immutable: upload alone never opens a material, and a new material version requires explicit activity binding. Existing player quests retain their activity version/question set. New raw course problems except reviewed 476 are archived unpublished and cannot be fetched by students.
