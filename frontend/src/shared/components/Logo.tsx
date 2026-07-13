// PULSE 브랜드 로고 (public/pulse-logo.png, 투명 배경 정사각 PNG).
// 볼 원 안에 워드마크가 담겨 있어 배경 칩 없이 밝은·어두운 배경 모두에서 보인다.
function Logo() {
  return (
    <img
      src="/pulse-logo.png"
      alt="PULSE"
      className="h-9 w-auto select-none"
    />
  );
}

export default Logo;
