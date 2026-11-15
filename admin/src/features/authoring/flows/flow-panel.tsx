import { Empty } from "@/components/common";
import { GroupedPicker } from "@/components/grouped-picker";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { RoutesEditor } from "@/features/authoring/flows/connections";
import { FlowSettings } from "@/features/authoring/flows/flow-settings";
import { NodeEditor } from "@/features/authoring/flows/node-editor";
import { FlowCanvas } from "@/features/authoring/graph/flow-canvas";
import {
  nodeTitle,
  routePorts,
  type Authoring as AuthoringData,
} from "@/features/authoring/model";
import { entryGroups, flowGroups } from "@/features/authoring/pickers";
import {
  AuthoringInspector,
  AuthoringWorkspace,
} from "@/features/authoring/workspace";
import { Plus, Settings2, Trash2 } from "lucide-react";
import type { useFlowEditor } from "../use-flow-editor";
export function FlowPanel({
  editor,
  data,
  change,
}: {
  editor: ReturnType<typeof useFlowEditor>;
  data: AuthoringData;
  change: (next: AuthoringData) => void;
}) {
  const {
    setSelectedFlow,
    selectedNode,
    selectedEntry,
    inspector,
    setInspector,
    pendingEntry,
    setPendingEntry,
    graphFocus,
    setGraphFocus,
    settingsOpen,
    setSettingsOpen,
    layoutVersion,
    setLayoutVersion,
    flow,
    node,
    layouts,
    entryLayouts,
    positions,
    entryPositions,
    entry,
    replaceFlow,
    selectNode,
    selectEntry,
    connect,
    replaceNode,
    addFlow,
    addEntry,
    addNode,
    removeNode,
  } = editor;
  const availableEntries = editor.flow ? entryGroups(data, editor.flow) : [];
  const entryLabel = (key: string) =>
    key.startsWith("interaction:")
      ? (data.configuration.world.interactions?.find(
          (i) => `interaction:${i.id}` === key,
        )?.label ?? "地图交互")
      : (data.catalog.npcs.find((n) => n.id === key)?.name ?? "角色入口");
  const flowToolbar = (
    <>
      <GroupedPicker
        className="w-52"
        label="选择流程"
        placeholder="选择流程"
        value={flow?.id}
        groups={flowGroups(data.flows, data.catalog.chapters)}
        onChange={(id) => {
          setSelectedFlow(id);
          selectNode(null);
          setInspector(null);
          setGraphFocus(undefined);
        }}
      />
      <Button size="sm" variant="outline" onClick={addFlow}>
        <Plus />
        新建流程
      </Button>
      {flow && (
        <>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setSettingsOpen(true)}
          >
            <Settings2 />
            流程设置与入口
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="删除流程"
            title="删除流程"
            onClick={() => {
              if (!window.confirm(`删除「${flow.title}」及其流程入口？`))
                return;
              const remaining = data.flows.filter(
                (item) => item.id !== flow.id,
              );
              change({ ...data, flows: remaining });
              setSelectedFlow(remaining[0]?.id ?? null);
              selectNode(null);
              setInspector(null);
            }}
          >
            <Trash2 />
          </Button>
        </>
      )}
    </>
  );

  return (
    <>
      {" "}
      {flow ? (
        <>
          <FlowCanvas
            key={`${flow.id}:${layoutVersion}`}
            nodes={flow.content.nodes}
            labels={Object.fromEntries(
              flow.content.nodes.map((n) => [
                n.id,
                nodeTitle(n, data.catalog, flow),
              ]),
            )}
            entries={Object.entries(flow.content.entries).map(
              ([key, routes]) => ({
                id: key,
                title: entryLabel(key),
                ports: routePorts(routes),
              }),
            )}
            positions={positions}
            entryPositions={entryPositions}
            selected={selectedNode}
            selectedEntry={selectedEntry}
            focusTarget={graphFocus}
            onSelect={selectNode}
            onSelectEntry={selectEntry}
            onInspect={(source, point, trigger) => {
              if (source.kind === "entry") selectEntry(source.id);
              else selectNode(source.id);
              setInspector({ point, trigger });
            }}
            onDismissInspector={() => setInspector(null)}
            renderWorkspace={({ palette, canvas }) => (
              <AuthoringWorkspace
                tools={palette}
                toolbar={flowToolbar}
                className="authoring-flow-workspace"
              >
                {canvas}
              </AuthoringWorkspace>
            )}
            onConnect={connect}
            onAddNode={addNode}
            onAddEntry={setPendingEntry}
            entryAvailable={
              availableEntries.length > 0 &&
              Object.keys(flow.content.entries).length < 30
            }
            onMove={(id, point) =>
              replaceFlow(flow, { ...positions, [id]: point })
            }
            onMoveEntry={(id, point) =>
              change({
                ...data,
                configuration: {
                  ...data.configuration,
                  editor: {
                    ...data.configuration.editor,
                    flow_entry_layouts: {
                      ...entryLayouts,
                      [flow.id]: { ...entryPositions, [id]: point },
                    },
                  },
                },
              })
            }
            onAutoArrange={() => {
              change({
                ...data,
                configuration: {
                  ...data.configuration,
                  editor: {
                    ...data.configuration.editor,
                    flow_layouts: { ...layouts, [flow.id]: {} },
                    flow_entry_layouts: { ...entryLayouts, [flow.id]: {} },
                  },
                },
              });
              setLayoutVersion((v) => v + 1);
            }}
          />
          <AuthoringInspector
            anchor={node || entry ? (inspector?.point ?? null) : null}
            returnFocus={inspector?.trigger}
            title={node ? "节点属性" : "入口属性"}
            onClose={() => setInspector(null)}
          >
            {node ? (
              <NodeEditor
                key={node.id}
                node={node}
                flow={flow}
                catalog={data.catalog}
                onChange={replaceNode}
                onFlowChange={replaceFlow}
                onDelete={removeNode}
              />
            ) : selectedEntry && entry ? (
              <div className="node-properties" key={selectedEntry}>
                <p className="hint">触发入口</p>
                <h2 className="font-semibold mb-3">
                  {entryLabel(selectedEntry)}
                </h2>
                <RoutesEditor
                  routes={entry}
                  onChange={(routes) =>
                    replaceFlow({
                      ...flow,
                      content: {
                        ...flow.content,
                        entries: {
                          ...flow.content.entries,
                          [selectedEntry]: routes,
                        },
                      },
                    })
                  }
                  flow={flow}
                  catalog={data.catalog}
                />
                <Button
                  variant="ghost"
                  size="sm"
                  className="mt-3"
                  onClick={() => {
                    if (!window.confirm("移除此触发入口？节点会保留。")) return;
                    const nextEntries = { ...flow.content.entries };
                    delete nextEntries[selectedEntry];
                    replaceFlow({
                      ...flow,
                      content: { ...flow.content, entries: nextEntries },
                    });
                    selectNode(null);
                  }}
                >
                  <Trash2 />
                  移除入口
                </Button>
              </div>
            ) : null}
          </AuthoringInspector>
        </>
      ) : (
        <AuthoringWorkspace
          tools={<p className="hint p-2">先新建流程，再添加节点。</p>}
          toolbar={flowToolbar}
        >
          <Empty
            title="选择或创建一个流程"
            description="公共流程可以贯穿各章节。章节流程归属具体教学章节。"
          />
        </AuthoringWorkspace>
      )}
      {settingsOpen && flow && (
        <FlowSettings
          flow={flow}
          catalog={data.catalog}
          entries={availableEntries}
          onChange={replaceFlow}
          onClose={() => setSettingsOpen(false)}
          onAddEntry={addEntry}
        />
      )}
      {pendingEntry && flow && (
        <Dialog
          open
          onOpenChange={(open) => {
            if (!open) setPendingEntry(null);
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>添加触发入口</DialogTitle>
              <DialogDescription>
                选择触发流程的 NPC 或交互点，再在画布上把入口连到起始节点。
              </DialogDescription>
            </DialogHeader>
            <GroupedPicker
              label="选择入口位置"
              placeholder="选择地图位置"
              groups={availableEntries}
              onChange={(id) => addEntry(id, pendingEntry)}
            />
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}
