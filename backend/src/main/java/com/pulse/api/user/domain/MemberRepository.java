package com.pulse.api.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);

    // 로그인 시 이메일로 회원 조회
    Optional<Member> findByEmail(String email);

    /**
     * 선호 설정을 변경할 회원을 비관적 쓰기 잠금으로 조회합니다.
     *
     * 같은 사용자의 관심팀·관심 선수 변경 요청이 동시에 들어오면
     * 먼저 이 회원 행을 잠근 트랜잭션이 끝날 때까지
     * 나머지 요청은 대기합니다.
     *
     * 이를 통해 관심 선수 최대 5명 검증과 관계 저장이
     * 사용자별로 순차 실행되도록 보장합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT member
        FROM Member member
        WHERE member.email = :email
        """)
    Optional<Member> findByEmailForUpdate(
            @Param("email") String email
    );
}