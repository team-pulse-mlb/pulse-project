interface EmptyStateProps {
  message: string;
}

// 데이터 없음 안내 (dashed 카드).
function EmptyState({ message }: EmptyStateProps) {
  return (
    <div className="rounded-panel border border-dashed border-[#C9D0DB] bg-white/60 px-6 py-10 text-center text-sm text-text-muted">
      {message}
    </div>
  );
}

export default EmptyState;
