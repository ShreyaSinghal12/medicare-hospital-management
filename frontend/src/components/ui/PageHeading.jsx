import { useEffect, useRef } from 'react';
import { usePageFocusSignal } from '../../context/PageFocusContext.jsx';

/**
 * The page's single h1. Sets the document title ("<title> · MediCare") and, after an in-app navigation, moves focus
 * here so keyboard and screen-reader users start at the new page's heading.
 */
export default function PageHeading({ title, className = '', children }) {
  const ref = useRef(null);
  const navigation = usePageFocusSignal();
  const focusedFor = useRef(-1);

  useEffect(() => {
    if (title) document.title = `${title} · MediCare`;
  }, [title]);

  useEffect(() => {
    if (navigation > 0 && focusedFor.current !== navigation && ref.current) {
      focusedFor.current = navigation;
      ref.current.focus({ preventScroll: true });
    }
  }, [navigation]);

  return <h1 ref={ref} tabIndex={-1} className={`${className} focus:outline-none`}>{children ?? title}</h1>;
}
