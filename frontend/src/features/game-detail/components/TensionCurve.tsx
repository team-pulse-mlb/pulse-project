import { useId } from 'react';

import Card from '../../../shared/components/Card';
import type { DisplayMode } from '../lib/displayMode';

/**
 * 서버가 계산한 긴장도 한 지점이다.
 *
 * 프론트엔드에서는 level 값을 재계산하거나 임의로 증폭하지 않고
 * 전달받은 1~5 값을 그대로 그래프에 사용한다.
 */
export interface TensionPoint {
  inning: number;

  /**
   * 보호 응답에는 포함되지 않을 수 있다.
   * 공개 모드에서만 초·말 라벨 표시에 사용한다.
   */
  inningType?: 'TOP' | 'BOTTOM';

  level: 1 | 2 | 3 | 4 | 5;
}

interface TensionCurveProps {
  mode: DisplayMode;
  points: TensionPoint[];
}

const WIDTH = 640;
const HEIGHT = 180;
const PADDING_X = 24;
const PADDING_TOP = 16;
const PADDING_BOTTOM = 32;

function getInningLabel(point: TensionPoint, mode: DisplayMode) {
  /**
   * 보호 모드에서는 현재 공격 방향을 추측할 수 없도록
   * 이닝 숫자만 표시한다.
   */
  if (mode === 'PROTECTED' || !point.inningType) {
    return String(point.inning);
  }

  return `${point.inning}${
      point.inningType === 'TOP' ? '초' : '말'
  }`;
}

function TensionCurve({ mode, points }: TensionCurveProps) {
  const gradientId = useId().replace(/:/g, '');

  if (points.length === 0) {
    return null;
  }

  const innerWidth = WIDTH - PADDING_X * 2;
  const innerHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;

  const getX = (index: number) =>
      PADDING_X +
      (points.length === 1
          ? innerWidth / 2
          : (index / (points.length - 1)) * innerWidth);

  const getY = (level: TensionPoint['level']) =>
      PADDING_TOP + ((5 - level) / 4) * innerHeight;

  /**
   * 서버 레벨을 순서대로 직선 연결한다.
   * 부드러운 곡선 보간이나 임의의 진폭은 추가하지 않는다.
   */
  const linePath = points
      .map(
          (point, index) =>
              `${index === 0 ? 'M' : 'L'} ${getX(index).toFixed(1)} ${getY(
                  point.level,
              ).toFixed(1)}`,
      )
      .join(' ');

  const baselineY = HEIGHT - PADDING_BOTTOM;

  const areaPath = [
    linePath,
    `L ${getX(points.length - 1).toFixed(1)} ${baselineY}`,
    `L ${getX(0).toFixed(1)} ${baselineY}`,
    'Z',
  ].join(' ');

  return (
      <Card>
        <h3 className="text-[15px] font-bold text-text-strong">
          경기 흐름
        </h3>

        <p className="mb-4 mt-1 text-[13px] text-text-faint">
          점수를 드러내지 않는 이닝별 긴장도입니다
        </p>

        <div className="overflow-x-auto">
          <svg
              viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
              className="min-w-[480px] w-full"
              role="img"
              aria-label="이닝별 긴장도 곡선"
          >
            <defs>
              <linearGradient
                  id={gradientId}
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
              >
                <stop
                    offset="0%"
                    stopColor="#E4002B"
                    stopOpacity="0.18"
                />
                <stop
                    offset="100%"
                    stopColor="#E4002B"
                    stopOpacity="0"
                />
              </linearGradient>
            </defs>

            {/* 세로축 숫자는 표시하지 않고 이닝 위치만 구분한다. */}
            {points.map((point, index) => (
                <line
                    key={`grid-${point.inning}-${point.inningType ?? 'FULL'}-${index}`}
                    x1={getX(index)}
                    x2={getX(index)}
                    y1={PADDING_TOP}
                    y2={baselineY}
                    stroke="#F1F3F6"
                    strokeWidth="1"
                />
            ))}

            <path
                d={areaPath}
                fill={`url(#${gradientId})`}
            />

            <path
                d={linePath}
                fill="none"
                stroke="#E4002B"
                strokeWidth="2.5"
                strokeLinejoin="round"
                strokeLinecap="round"
            />

            {points.map((point, index) => (
                <text
                    key={`label-${point.inning}-${point.inningType ?? 'FULL'}-${index}`}
                    x={getX(index)}
                    y={HEIGHT - 10}
                    textAnchor="middle"
                    className="fill-text-faint"
                    fontSize="11"
                >
                  {getInningLabel(point, mode)}
                </text>
            ))}
          </svg>
        </div>
      </Card>
  );
}

export default TensionCurve;