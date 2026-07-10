// PULSE 브랜드 로고 (야구공 실밥 = 심전도 파형 메타포).
// 프로토타입 헤더의 간결화 버전 SVG를 그대로 옮겼다.
function Logo({ dark = true }: { dark?: boolean }) {
  const waveColor = dark ? '#FFFFFF' : '#002D72';

  return (
    <span className="inline-flex items-center gap-2.5">
      <svg
        viewBox="0 0 100 100"
        className="h-7 w-7"
        aria-hidden="true"
        role="img"
      >
        <circle
          cx="50"
          cy="50"
          r="46"
          fill={dark ? 'rgba(255,255,255,0.08)' : '#FFFFFF'}
          stroke="#E4002B"
          strokeOpacity="0.35"
          strokeWidth="4"
        />
        <polyline
          points="10,55 30,55 38,55 47,71 53,25 61,60 66,55 90,55"
          fill="none"
          stroke={waveColor}
          strokeWidth="6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <rect
          x="46"
          y="8"
          width="9"
          height="9"
          fill="#E4002B"
          transform="rotate(45 50.5 12.5)"
        />
      </svg>
      <span
        className={`font-display text-[21px] font-bold tracking-[0.16em] ${
          dark ? 'text-white' : 'text-mlb-navy'
        }`}
      >
        PULSE
      </span>
    </span>
  );
}

export default Logo;
