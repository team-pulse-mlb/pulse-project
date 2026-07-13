import type { ReactNode } from 'react';

interface InfoRowProps {
  label: string;
  value: ReactNode;
}

// 라벨-값 정보 행: 예정 경기 정보, 마이페이지, 설정 화면 공용.
// 행 사이는 구분선으로 나뉜다 (부모에서 divide-y 없이 자체 border-top 사용).
function InfoRow({ label, value }: InfoRowProps) {
  return (
    <div className="flex items-center justify-between gap-4 border-t border-divider py-3.5 first:border-t-0 first:pt-0 last:pb-0">
      <span className="text-sm text-text-muted">{label}</span>
      <span className="text-right text-[15px] font-medium text-text-strong">
        {value}
      </span>
    </div>
  );
}

export default InfoRow;
