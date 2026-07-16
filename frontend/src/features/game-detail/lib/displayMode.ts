// 보호/공개 모드 저장 정책 (USER_FLOW §3.7 / §5):
// - 서버 저장 없음, 이 브라우저의 localStorage에만 저장한다.
// - 새로고침은 현재 모드를 유지한다.
// - 카드/목록/알림에서 경기 상세로 다시 진입하면 항상 보호 모드로 시작한다.
// - 따라서 경기 ID만 저장 키로 쓰지 않고, React Router의 상세 진입 key까지 함께 저장한다.

export type DisplayMode = 'PROTECTED' | 'REVEALED';

const FALLBACK_ENTRY_KEY = 'direct-entry';

function normalizeEntryKey(entryKey: string): string {
  return entryKey.trim().length > 0
    ? entryKey
    : FALLBACK_ENTRY_KEY;
}

function storageKey(gameId: string, entryKey: string): string {
  return `pulse.mode.${gameId}.${normalizeEntryKey(entryKey)}`;
}

export function getStoredMode(
  gameId: string,
  entryKey: string,
): DisplayMode {
  return localStorage.getItem(storageKey(gameId, entryKey)) === 'REVEALED'
    ? 'REVEALED'
    : 'PROTECTED';
}

export function storeMode(
  gameId: string,
  entryKey: string,
  mode: DisplayMode,
) {
  localStorage.setItem(storageKey(gameId, entryKey), mode);
}
