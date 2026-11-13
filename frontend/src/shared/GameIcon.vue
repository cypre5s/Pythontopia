<template>
  <component :is="icons[name]" class="game-icon" :size="24" :stroke-width="1.7" aria-hidden="true" />
</template>
<script setup lang="ts">
import {
  LoaderCircle, FileText, CodeXml, ListChecks, Terminal, Target, List, Play, Square, Lightbulb, ChevronDown, MessageSquare, NotebookText, BookOpen, Users, Settings, Compass, House,
  X, Leaf, Music, Video, SquarePen, ChevronRight, Grid2X2, Folder,
  Activity, ChartColumnIncreasing, Upload, LogOut, Plus, Copy, RefreshCw,
  ArrowLeft, ArrowRight, ArrowUp, ArrowDown, ExternalLink,
  Check, CircleX, Clock, CircleAlert, PanelRightClose, PanelRightOpen,
} from "@lucide/vue";
const icons = {
  spinner: LoaderCircle, description: FileText, code: CodeXml, cases: ListChecks, terminal: Terminal, target: Target, list: List,
  play: Play, stop: Square, hint: Lightbulb, chevronDown: ChevronDown,
  message: MessageSquare, journal: NotebookText, book: BookOpen, people: Users,
  settings: Settings, compass: Compass, building: House, close: X, leaf: Leaf,
  music: Music, video: Video, practice: SquarePen, chevron: ChevronRight,
  grid: Grid2X2, folder: Folder, activity: Activity, chart: ChartColumnIncreasing,
  publish: Upload, logout: LogOut, plus: Plus, copy: Copy, refresh: RefreshCw,
  arrowLeft: ArrowLeft, arrowRight: ArrowRight, arrowUp: ArrowUp, arrowDown: ArrowDown,
  external: ExternalLink, check: Check, circleX: CircleX, clock: Clock,
  alertCircle: CircleAlert, panelClose: PanelRightClose, panelOpen: PanelRightOpen,
};
defineProps<{ name: keyof typeof icons }>();
</script>
