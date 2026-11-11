import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';

/** Increments on every in-app navigation (0 on first load) so the page heading can take focus after a route change. */
export const PageFocusContext = createContext(0);

export function usePageFocusSignal() {
  return useContext(PageFocusContext);
}

/** Counts route changes after the first page (used by the layouts to feed {@link PageFocusContext}). */
export function useNavigationCounter() {
  const { pathname } = useLocation();
  const [count, setCount] = useState(0);
  const first = useRef(pathname);
  useEffect(() => {
    if (pathname !== first.current) {
      first.current = null;
      setCount((n) => n + 1);
    }
  }, [pathname]);
  return count;
}
