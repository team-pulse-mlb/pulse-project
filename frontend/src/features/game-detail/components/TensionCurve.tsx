import Card from '../../../shared/components/Card';

// 긴장 곡선(펄스 그래프) — 종료 경기 전용 (SPOILER_POLICY §3·§5).
// 서버가 1~5단계로 양자화한 레벨만 받는다. 세로축 눈금·숫자 금지, 가로축 이닝 라벨만 허용.
export interface TensionPoint {
  inning: number;
  /** 공개 모드만 값이 있다 (하프이닝 단위) */
  inningType?: 'TOP' | 'BOTTOM';
  level: 1 | 2 | 3 | 4 | 5;
  /** 이벤트 노드 라벨 (있는 지점에만 마커 표시) */
  eventLabel?: string;
}

interface TensionCurveProps {
  points: TensionPoint[];
}

const WIDTH = 640;
const HEIGHT = 180;
const PADDING_X = 24;
const PADDING_TOP = 16;
const PADDING_BOTTOM = 32;

function TensionCurve({ points }: TensionCurveProps) {
  if (points.length === 0) {
    return null;
  }

  const innerWidth = WIDTH - PADDING_X * 2;
  const innerHeight = HEIGHT - PADDING_TOP - PADDING_BOTTOM;

  const x = (index: number) =>
    PADDING_X + (points.length === 1 ? innerWidth / 2 : (index / (points.length - 1)) * innerWidth);
  const y = (level: number) => PADDING_TOP + ((5 - level) / 4) * innerHeight;

  const path = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(p.level).toFixed(1)}`)
    .join(' ');

  const areaPath = `${path} L ${x(points.length - 1).toFixed(1)} ${HEIGHT - PADDING_BOTTOM} L ${x(0).toFixed(1)} ${HEIGHT - PADDING_BOTTOM} Z`;

  return (
    <Card>
      <h3 className="mb-3 text-[15px] font-bold text-text-strong">경기 흐름</h3>
      <p className="mb-4 text-[13px] text-text-faint">
        점수를 드러내지 않는 긴장도 곡선입니다
      </p>

      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="min-w-[480px]"
          role="img"
          aria-label="이닝별 긴장도 곡선"
        >
          <defs>
            <linearGradient id="pulseArea" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#E4002B" stopOpacity="0.18" />
              <stop offset="100%" stopColor="#E4002B" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* 이닝 격자선 */}
          {points.map((_, i) => (
            <line
              key={i}
              x1={x(i)}
              x2={x(i)}
              y1={PADDING_TOP}
              y2={HEIGHT - PADDING_BOTTOM}
              stroke="#F1F3F6"
              strokeWidth="1"
            />
          ))}

          <path d={areaPath} fill="url(#pulseArea)" />
          <path
            d={path}
            fill="none"
            stroke="#E4002B"
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
          />

          {/* 이벤트 노드 (마름모) — 탭/호버 시 라벨 확인 */}
          {points.map(
            (p, i) =>
              p.eventLabel && (
                <g key={`marker-${i}`}>
                  <rect
                    x={x(i) - 5}
                    y={y(p.level) - 5}
                    width="10"
                    height="10"
                    rx="2"
                    transform={`rotate(45 ${x(i)} ${y(p.level)})`}
                    fill="#002D72"
                  >
                    <title>{p.eventLabel}</title>
                  </rect>
                </g>
              ),
          )}

          {/* 가로축: 이닝 라벨만 허용 (세로축 눈금·숫자 금지) */}
          {points.map((p, i) => (
            <text
              key={`label-${i}`}
              x={x(i)}
              y={HEIGHT - 10}
              textAnchor="middle"
              className="fill-text-faint"
              fontSize="11"
            >
              {p.inningType ? `${p.inning}${p.inningType === 'TOP' ? '초' : '말'}` : p.inning}
            </text>
          ))}
        </svg>
      </div>
    </Card>
  );
}

export default TensionCurve;
