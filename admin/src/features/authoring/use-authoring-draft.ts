import { api, ApiError, errorMessage } from "@/lib/api";
import { useDraft } from "@/lib/drafts";
import { useState } from "react";
import type { Diagnostic, ResetImpact, Validation } from "./contracts";
import type { Authoring } from "./model";
import { authoringBody } from "./request";
export function useAuthoringDraft(initial: Authoring, classroomId: number) {
  const [data, setData] = useState(initial),
    [savedData, setSavedData] = useState(initial);
  const [draftEpoch, setDraftEpoch] = useState(0),
    [assetBusy, setAssetBusy] = useState(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]),
    [resetImpact, setResetImpact] = useState<ResetImpact>();
  const [confirmReset, setConfirmReset] = useState(false),
    [impactChanged, setImpactChanged] = useState(false);
  const dirty = data !== savedData;
  const base = `/teacher/classrooms/${classroomId}/authoring`;
  useDraft(dirty);
  function change(next: Authoring) {
    setData({
      ...next,
      catalog: {
        ...next.catalog,
        npcs: next.npcs.map((npc) => ({
          id: npc.id,
          name: npc.name,
          map_key: npc.placement?.map_key,
        })),
      },
    });
    setMessage("");
    setDiagnostics([]);
    setResetImpact(undefined);
  }
  function discard() {
    setData(savedData);
    setError("");
    setMessage("");
    setDiagnostics([]);
    setResetImpact(undefined);
    setDraftEpoch((epoch) => epoch + 1);
  }
  async function validate(save = false, confirmationToken?: string) {
    if (busy || assetBusy) return;
    setBusy(true);
    setError("");
    setMessage("");
    setImpactChanged(false);
    try {
      if (!confirmationToken) {
        const result = await api.post<Validation>(
          `${base}/validate`,
          authoringBody(data),
        );
        setDiagnostics(result.diagnostics);
        setResetImpact(result.reset_impact);
        if (!result.valid) return;
        if (result.reset_impact) {
          setConfirmReset(save);
          return;
        }
        if (!save) {
          setMessage("校验通过：流程连接、条件和引用有效。");
          return;
        }
      }
      const saved = await api.put<{ revision: number }>(base, {
        ...authoringBody(data),
        confirmation_token: confirmationToken,
      });
      const next = { ...data, revision: saved.revision };
      setData(next);
      setSavedData(next);
      setResetImpact(undefined);
      setConfirmReset(false);
      setMessage("课程已保存。");
    } catch (e) {
      const detail = e instanceof ApiError ? e.detail : null;
      if (
        detail &&
        typeof detail === "object" &&
        "reset_impact" in detail &&
        detail.reset_impact
      ) {
        setResetImpact(detail.reset_impact as ResetImpact);
        setImpactChanged(Boolean(confirmationToken));
        setConfirmReset(true);
      } else {
        setConfirmReset(false);
        setResetImpact(undefined);
        if (
          detail &&
          typeof detail === "object" &&
          "diagnostics" in detail &&
          Array.isArray(detail.diagnostics)
        ) {
          setDiagnostics(detail.diagnostics as Diagnostic[]);
        } else setError(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  }

  return {
    data,
    savedData,
    change,
    discard,
    dirty,
    draftEpoch,
    assetBusy,
    setAssetBusy,
    busy,
    setBusy,
    error,
    setError,
    message,
    diagnostics,
    resetImpact,
    confirmReset,
    setConfirmReset,
    impactChanged,
    validate,
  };
}
