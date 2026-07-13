import { useLayoutEffect, useRef } from 'react';

const FLIP_TRANSITION_DURATION_MS = 350;
const FLIP_TRANSITION_EASING = 'ease-in-out';
const FLIP_CLEANUP_DELAY_MS = FLIP_TRANSITION_DURATION_MS + 50;

type AnimationCleanup = () => void;

// orderKey는 추천 카드의 순서를 나타내는 문자열이다.
// 이 값이 바뀔 때(=랭킹 재정렬)만 FLIP을 측정·실행해, 탭 전환 같은
// 순서와 무관한 리렌더에서 카드가 흔들리는 문제를 막는다.
function useFlipAnimation(orderKey: string) {
  const containerRef = useRef<HTMLDivElement>(null);
  const previousRectsRef = useRef(new Map<string, DOMRect>());
  const animationCleanupsRef = useRef(new Map<string, AnimationCleanup>());

  useLayoutEffect(() => {
    const container = containerRef.current;

    if (!container) {
      return;
    }

    const elements = Array.from(
      container.querySelectorAll<HTMLElement>('[data-flip-id]'),
    );
    const elementsById = new Map(
      elements.map((element) => [element.dataset.flipId!, element]),
    );
    const interruptedRects = new Map<string, DOMRect>();

    animationCleanupsRef.current.forEach((cleanup, flipId) => {
      const element = elementsById.get(flipId);

      if (element) {
        interruptedRects.set(flipId, element.getBoundingClientRect());
      }

      cleanup();
    });
    animationCleanupsRef.current.clear();

    const currentRects = new Map(
      elements.map((element) => [
        element.dataset.flipId!,
        element.getBoundingClientRect(),
      ]),
    );

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      previousRectsRef.current = currentRects;
      return;
    }

    const animatedElements: HTMLElement[] = [];

    elements.forEach((element) => {
      const flipId = element.dataset.flipId!;
      const firstRect = interruptedRects.get(flipId) ?? previousRectsRef.current.get(flipId);
      const lastRect = currentRects.get(flipId)!;

      if (!firstRect || firstRect.width === 0 || firstRect.height === 0) {
        return;
      }

      const translateX = firstRect.left - lastRect.left;
      const translateY = firstRect.top - lastRect.top;
      const scaleX = firstRect.width / lastRect.width;
      const scaleY = firstRect.height / lastRect.height;

      if (
        translateX === 0 &&
        translateY === 0 &&
        scaleX === 1 &&
        scaleY === 1
      ) {
        return;
      }

      element.style.transformOrigin = 'top left';
      element.style.transition = 'none';
      element.style.transform = `translate(${translateX}px, ${translateY}px) scale(${scaleX}, ${scaleY})`;
      animatedElements.push(element);
    });

    // invert 스타일을 브라우저에 확정한 뒤 최종 위치로 transition을 시작한다.
    void container.offsetHeight;

    animatedElements.forEach((element) => {
      const flipId = element.dataset.flipId!;

      const cleanup = () => {
        window.clearTimeout(cleanupTimer);
        element.removeEventListener('transitionend', handleTransitionEnd);
        element.style.removeProperty('transform');
        element.style.removeProperty('transition');
        element.style.removeProperty('transform-origin');
        animationCleanupsRef.current.delete(flipId);
      };
      const handleTransitionEnd = (event: TransitionEvent) => {
        if (event.target === element && event.propertyName === 'transform') {
          cleanup();
        }
      };

      element.addEventListener('transitionend', handleTransitionEnd);
      element.style.transition = `transform ${FLIP_TRANSITION_DURATION_MS}ms ${FLIP_TRANSITION_EASING}`;
      element.style.removeProperty('transform');
      const cleanupTimer = window.setTimeout(cleanup, FLIP_CLEANUP_DELAY_MS);
      animationCleanupsRef.current.set(flipId, cleanup);
    });

    previousRectsRef.current = currentRects;
  }, [orderKey]);

  useLayoutEffect(
    () => () => {
      animationCleanupsRef.current.forEach((cleanup) => cleanup());
      animationCleanupsRef.current.clear();
    },
    [],
  );

  return containerRef;
}

export default useFlipAnimation;
