import { useEffect, useState } from 'react';
import { useApp } from '../context/AppContext';

export function AnimatedNumber({
  value,
  formatter,
  className = '',
}: {
  value: number;
  formatter?: (n: number) => string;
  className?: string;
}) {
  const { reducedMotion } = useApp();
  const [display, setDisplay] = useState(value);

  useEffect(() => {
    if (reducedMotion || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setDisplay(value);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const from = display;
    const delta = value - from;
    if (Math.abs(delta) < 0.01) {
      setDisplay(value);
      return;
    }
    const duration = 260;
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      setDisplay(from + delta * eased);
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, reducedMotion]);

  return (
    <span className={`font-mono-num ${className}`}>
      {formatter ? formatter(display) : display.toFixed(2)}
    </span>
  );
}
