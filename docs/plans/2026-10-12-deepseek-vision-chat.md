# DeepSeek Vision Chat Implementation Plan

> **For Codex:** Implement the approved design in this plan task by task.

**Goal:** Route tutor and workshop text-plus-image questions to DeepSeek vision and return streamed text.

**Architecture:** Reuse the existing tutor API and LLM provider. Validate image data in the Graph request schema, forward image blocks only to the final TutorAgent call, and select the separately configured vision model for image questions. Add inline image handling to the existing workshop composer while preserving document uploads and the NPC fast model.

**Tech Stack:** Vue 3 + TypeScript, Spring Boot, FastAPI + Pydantic, OpenAI-compatible DeepSeek Chat Completions.

---

### Task 1: Forward and validate tutor images

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorGraphClient.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/dto/TutorAskRequest.java`
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/tutor.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/tutor_agent.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/tutor.py`
- Update constructor fixtures in: `backend/src/test/java/com/pythontopia/tutor/TutorGraphClientFailureTest.java` and `backend/src/test/java/com/pythontopia/tutor/TutorGraphClientSseTest.java`

**Steps:** Forward images unchanged from Spring to Graph; validate count, size, MIME, and base64 at the Graph boundary; attach image URL parts to the final user message; use the separately configured vision model for image calls; keep review and SSE output unchanged.

### Task 2: Add workshop image attachments

**Files:**
- Modify: `frontend/src/components/workshop/ChatComposer.vue`
- Modify: `frontend/src/components/workshop/AttachmentPills.vue`
- Modify: `frontend/src/components/workshop/WorkshopChat.vue`
- Modify: `frontend/src/api/tutor.ts`

**Steps:** Accept supported image files alongside existing documents, read images as Data URLs, enforce the shared image limits, carry images through the existing tutor request for normal answer intents, and leave document extraction untouched.

### Task 3: Configure and document DeepSeek vision

**Files:**
- Update the local-only `deploy/.env` values `PYT_OPENAI_API_KEY` and `PYT_OPENAI_MODEL_VISION`, preserving the existing NPC fast model.
- Modify: `deploy/.env.example`
- Modify: `docs/operations/llm-provider-env.md`

**Steps:** Keep the API key out of tracked files; configure `deepseek-flash` in `PYT_OPENAI_MODEL_VISION`; document that it is separate from the NPC fast model.

### Task 4: Record the change

Update the repository changelog/work log. Run the focused Graph, Java, and frontend checks required by the repository instructions; do not send a live request with the configured credential. Review the final diff for accidental edits to the pre-existing dirty worktree.
