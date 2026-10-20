/** A small shuttlecock mark for the header and login card. */
export function Shuttle({ size = 20 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden>
      <path d="M8 3l2.2 9.5M16 3l-2.2 9.5M12 2v10.5M4.5 5.5l6 7.5M19.5 5.5l-6 7.5" />
      <path d="M9.5 12.5h5l-.5 3.5h-4z" />
      <circle cx="12" cy="19" r="3" fill="currentColor" stroke="none" />
    </svg>
  );
}
