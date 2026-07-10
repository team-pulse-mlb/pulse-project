// PULSE 브랜드 로고 (public/pulse-logo.png, 투명 배경 PNG).
// 워드마크가 네이비라서 다크 헤더에서는 흰 칩 배경 위에 올려 대비를 확보한다.
function Logo({ dark = true }: { dark?: boolean }) {
  const image = (
    <img src="/pulse-logo.png" alt="PULSE" className="h-8 w-auto select-none" />
  );

  if (!dark) {
    return image;
  }

  return (
    <span className="inline-flex items-center rounded-[10px] bg-white px-2.5 py-1">
      {image}
    </span>
  );
}

export default Logo;
