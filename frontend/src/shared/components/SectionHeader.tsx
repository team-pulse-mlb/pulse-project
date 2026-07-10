interface SectionHeaderProps {
  title: string;
  subtitle?: string;
}

// 섹션 헤더: 빨간 강조선(16×2px) + 제목 + 흐린 부제.
function SectionHeader({ title, subtitle }: SectionHeaderProps) {
  return (
    <div className="mb-4">
      <span className="mb-2 block h-0.5 w-4 rounded-full bg-mlb-red" />
      <h2 className="text-xl font-bold text-text-strong">{title}</h2>
      {subtitle && (
        <p className="mt-1 text-sm text-text-faint">{subtitle}</p>
      )}
    </div>
  );
}

export default SectionHeader;
