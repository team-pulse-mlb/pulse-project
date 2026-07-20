import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import DateNavigator from './DateNavigator';

afterEach(cleanup);

describe('DateNavigator', () => {
  it('브랜드 달력을 열고 선택한 날짜를 전달한다', () => {
    const onChange = vi.fn();
    render(<DateNavigator slateDate="2026-07-10" maxDate="2026-07-20" onChange={onChange} />);

    fireEvent.click(screen.getByRole('button', { name: '날짜 선택' }));

    expect(screen.getByRole('dialog', { name: '날짜 선택 달력' })).toBeTruthy();
    expect(screen.getByText('2026년 7월')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: '2026년 7월 19일' }));

    expect(onChange).toHaveBeenCalledWith('2026-07-19');
    expect(screen.queryByRole('dialog', { name: '날짜 선택 달력' })).toBeNull();
  });

  it('오늘 이후 날짜와 다음 달 이동을 비활성화한다', () => {
    render(<DateNavigator slateDate="2026-07-20" maxDate="2026-07-20" onChange={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '날짜 선택' }));

    expect(screen.getByRole('button', { name: '2026년 7월 21일' })).toHaveProperty('disabled', true);
    expect(screen.getByRole('button', { name: '다음 달' })).toHaveProperty('disabled', true);
    expect(screen.getByRole('button', { name: '다음 날짜' })).toHaveProperty('disabled', true);
  });

  it('이전 달을 탐색하고 ESC로 달력을 닫는다', () => {
    render(<DateNavigator slateDate="2026-07-10" maxDate="2026-07-20" onChange={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '날짜 선택' }));
    fireEvent.click(screen.getByRole('button', { name: '이전 달' }));
    expect(screen.getByText('2026년 6월')).toBeTruthy();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: '날짜 선택 달력' })).toBeNull();
  });
});
