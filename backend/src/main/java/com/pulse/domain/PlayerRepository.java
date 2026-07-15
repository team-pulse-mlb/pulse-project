package com.pulse.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** 이름이 채워지지 않은 스텁 선수 조회. 보강 배치의 런당 상한 제한용으로 Pageable을 받는다. */
    List<Player> findByFullNameIsNull(Pageable pageable);


    /**
     * 관심 선수 설정 화면에서 선수 이름을 검색합니다.
     *
     * 검색 정책:
     * - 이름이 없는 스텁 선수 제외
     * - 영문 이름 일부 검색
     * - 대소문자 구분 없음
     * - 선수 이름 오름차순
     * - 반환 개수는 호출 측의 Pageable로 제한
     */
    List<Player>
    findByFullNameIsNotNullAndFullNameContainingIgnoreCaseOrderByFullNameAsc(
            String keyword,
            Pageable pageable
    );
}
