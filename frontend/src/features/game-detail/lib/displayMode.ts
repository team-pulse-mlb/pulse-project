// 보호/공개 모드 저장 정책 (SPOILER_POLICY / USER_FLOW §3.7):
// - 서버 저장 없음, 브라우저 localStorage에 "경기 단위"로만 저장
// - 모든 경기는 항상 보호 모드로 시작

export type DisplayMode = 'PROTECTED' | 'REVEALED';

function storageKey(gameId: string): string {
  return `pulse.mode.${gameId}`;
}

export function getStoredMode(gameId: string): DisplayMode {
  return localStorage.getItem(storageKey(gameId)) === 'REVEALED'
    ? 'REVEALED'
    : 'PROTECTED';
}

export function storeMode(gameId: string, mode: DisplayMode) {
  localStorage.setItem(storageKey(gameId), mode);
}
