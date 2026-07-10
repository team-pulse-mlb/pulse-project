import type { ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';

import { queryClient } from '../shared/lib/queryClient';

// 앱 전역 프로바이더 조립 지점.
// 프로바이더가 늘어나면(테마, 인증 컨텍스트 등) 여기에만 추가한다.
function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

export default Providers;
