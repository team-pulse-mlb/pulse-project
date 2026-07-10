interface SkeletonProps {
  className?: string;
}

// 로딩 스켈레톤 블록. 크기는 사용처에서 className으로 지정한다.
function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={`animate-pulse rounded-panel bg-[#E3E7EE] ${className}`}
    />
  );
}

export default Skeleton;
