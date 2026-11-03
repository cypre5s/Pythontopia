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
