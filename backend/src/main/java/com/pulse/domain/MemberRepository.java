package com.pulse.domain;

import com.pulse.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);

    // 로그인 시 이메일로 회원 조회
    Optional<Member> findByEmail(String email);
}