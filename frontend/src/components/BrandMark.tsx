export function BrandMark({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      className="shrink-0"
    >
      <rect x="2" y="2" width="28" height="28" rx="7" stroke="currentColor" strokeWidth="2.2" />
      <path
        d="M7 18.5L12 13.5L16.5 17L24.5 9.5"
        stroke="var(--accent)"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="24.5" cy="9.5" r="2.2" fill="var(--accent)" />
      <path
        d="M7 23H25"
        stroke="currentColor"
        strokeOpacity="0.35"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}
