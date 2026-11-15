import { ErrorNotice, Loading } from "@/components/common";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AssistantEditor } from "@/features/authoring/characters/assistant-editor";
import { MapEditor } from "@/features/authoring/map/map-editor";
import { QuestEditor } from "@/features/authoring/quests/quest-editor";
import { useResource } from "@/lib/use-resource";
import { Bot, GitBranch, ListChecks, MapPinned } from "lucide-react";
import { AuthoringActions } from "./authoring-actions";
import type { Document } from "./contracts";
import { FlowPanel } from "./flows/flow-panel";
import { PreviewDialog } from "./preview-dialog";
import { useAuthoringDraft } from "./use-authoring-draft";
import { useAuthoringPreview } from "./use-authoring-preview";
import { useFlowEditor } from "./use-flow-editor";
import { ValidationFeedback } from "./validation-feedback";
export function Authoring({ classroomId }: { classroomId: number }) {
  const resource = useResource<Document>(
    `/teacher/classrooms/${classroomId}/authoring`,
  );
  return resource.loading ? (
    <Loading />
  ) : resource.error ? (
    <ErrorNotice message={resource.error} />
  ) : resource.data ? (
    <AuthoringEditor
      key={`${classroomId}-${resource.data.revision}`}
      initial={resource.data}
      classroomId={classroomId}
    />
  ) : null;
}

function AuthoringEditor({
  initial,
  classroomId,
}: {
  initial: Document;
  classroomId: number;
}) {
  const draft = useAuthoringDraft(initial, classroomId);
  const editor = useFlowEditor(draft.data, draft.change);
  const preview = useAuthoringPreview(initial, classroomId, draft);
  const { data, change, draftEpoch, busy, error, message, setAssetBusy } =
    draft;
  const {
    tab,
    setTab,
    setInspector,
    mapFocus,
    setMapFocus,
    editorJump,
    setEditorJump,
  } = editor;
  const entries = Array.from(
    new Set(data.flows.flatMap((f) => Object.keys(f.content.entries))),
  );
  function discardEdits() {
    draft.discard();
    editor.reset(draft.savedData);
    preview.setPreview(null);
  }
  function validate(save = false, token?: string) {
    editor.setInspector(null);
    void draft.validate(save, token);
  }
  return (
    <>
      <AuthoringActions
        draft={draft}
        hasEntries={entries.length > 0}
        onValidate={validate}
        onDiscard={discardEdits}
        onPreview={() => {
          preview.setPreviewEntry(entries[0]);
          preview.setPreview(null);
          preview.setPreviewOpen(true);
        }}
      />
      <ErrorNotice message={!preview.previewOpen ? error : ""} />
      {message && message !== "课程已保存。" && (
        <p role="status" className="notice-success">
          {message}
        </p>
      )}
      <ValidationFeedback draft={draft} editor={editor} onValidate={validate} />
      <div inert={busy} aria-busy={busy} data-testid="authoring-fields">
        <Tabs
          key={draftEpoch}
          value={tab}
          className="gap-3"
          onValueChange={(value) => {
            setTab(value);
            setInspector(null);
            setMapFocus(undefined);
            setEditorJump(undefined);
          }}
        >
          <TabsList variant="line" aria-label="教学流程视图">
            <TabsTrigger value="flows">
              <GitBranch />
              公共与章节流程
            </TabsTrigger>
            <TabsTrigger value="map">
              <MapPinned />
              地图交互与开放规则
            </TabsTrigger>
            <TabsTrigger value="quests">
              <ListChecks aria-hidden="true" />
              任务目标与依赖
            </TabsTrigger>
            <TabsTrigger value="assistant">
              <Bot aria-hidden="true" />
              助教教学角色
            </TabsTrigger>
          </TabsList>
          <TabsContent value="map">
            <MapEditor data={data} onChange={change} focusTarget={mapFocus} />
          </TabsContent>
          <TabsContent value="quests">
            <QuestEditor
              key={editorJump?.sequence}
              data={data}
              onChange={change}
              focusTarget={
                editorJump && {
                  questId: editorJump.target.quest_id ?? editorJump.target.id,
                  goalId:
                    editorJump.target.kind === "goal"
                      ? editorJump.target.id
                      : undefined,
                }
              }
            />
          </TabsContent>
          <TabsContent value="assistant">
            <AssistantEditor
              onUploadState={setAssetBusy}
              classroomId={classroomId}
              key={editorJump?.sequence}
              data={data}
              onChange={change}
              focusTarget={
                editorJump && {
                  roleId: editorJump.target.id,
                }
              }
            />
          </TabsContent>

          <TabsContent value="flows">
            <FlowPanel editor={editor} data={data} change={change} />
          </TabsContent>
        </Tabs>
      </div>
      <PreviewDialog initial={initial} draft={draft} state={preview} />
    </>
  );
}
