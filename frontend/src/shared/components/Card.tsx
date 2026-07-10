import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  /** 카드 내부 패딩 제거 (테이블·리스트를 가장자리까지 채울 때) */
  flush?: boolean;
  className?: string;
}

// 흰 카드 셸: 라이트 화면의 모든 콘텐츠 박스가 사용한다.
function Card({ children, flush = false, className = '' }: CardProps) {
  return (
    <section
      className={`rounded-card border border-card-border bg-white shadow-card ${
        flush ? '' : 'p-6'
      } ${className}`}
    >
      {children}
    </section>
  );
}

export default Card;
