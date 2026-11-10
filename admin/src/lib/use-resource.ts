import { useEffect, useState, useCallback } from "react";
import { api, errorMessage } from "./api";
export function useResource<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(Boolean(path)),
    [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((v) => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setData(null);
    setError("");
    setLoading(Boolean(path));
    if (path)
      api
        .get<T>(path, controller.signal)
        .then((value) => {
          if (active) setData(value);
        })
        .catch((e) => {
          if (active) setError(errorMessage(e));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    return () => {
      active = false;
      controller.abort();
    };
  }, [path, revision]);
  return { data, error, loading, reload };
}
