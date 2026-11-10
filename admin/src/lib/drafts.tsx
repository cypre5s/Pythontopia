import {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  type ReactNode,
} from "react";
const Context = createContext<{
  mark: (id: string, value: boolean) => void;
  canLeave: () => boolean;
}>({ mark: () => {}, canLeave: () => true });
export function DraftProvider({ children }: { children: ReactNode }) {
  const dirty = useRef(new Set<string>());
  const mark = (id: string, value: boolean) => {
    if (value) dirty.current.add(id);
    else dirty.current.delete(id);
  };
  const canLeave = () =>
    !dirty.current.size ||
    window.confirm("有尚未保存的修改。离开后将丢弃这些修改，是否继续？");
  useEffect(() => {
    const prevent = (e: BeforeUnloadEvent) => {
      if (dirty.current.size) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, []);
  return (
    <Context.Provider value={{ mark, canLeave }}>{children}</Context.Provider>
  );
}
export function useDraft(dirty: boolean) {
  const id = useId();
  const { mark } = useContext(Context);
  const saved = useRef(mark);
  saved.current = mark;
  useEffect(() => {
    saved.current(id, dirty);
    return () => saved.current(id, false);
  }, [id, dirty]);
}
export function useCanLeave() {
  return useContext(Context).canLeave;
}
