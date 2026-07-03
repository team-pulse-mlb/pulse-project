package com.pulse.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController // React 같은 클라이언트의 HTTP 요청을 처리하고, 데이터를 응답하는 컨트롤러 반환한 객체를 json으로 변환
                // RequestBody  + Cotroller
                // 반환값을 화면 이름으로 해석하지 말고 HTTP 응답 데이터로 보내라
@RequestMapping("api/members")  // 공통 주소
@Slf4j
@CrossOrigin(origins = "http://localhost:5173") // 임시 리엑트 연결 허용(로그인 창 미완성)
public class MemberController {

    // 회원가입 처리
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(
            @RequestBody Map<String, String> request
            // React가 HTTP 요청의 본문, 즉 body에 넣어서 보낸 JSON을 Java 객체로 바꿔주는 역할
    ) {
        String email = request.get("email");
        String password = request.get("password");

        // 비밀번호는 로그에 출력하면 안 됨
        log.info("회원가입 요청 email={}", email);

        return ResponseEntity.ok(   // ResponseEntity -> Spring에서 HTTP 응답을 직접 구성할 때 사용하는 클래스
                                    // ok()는 HTTP 상태 코드 200 OK로 응답하겠다는 뜻
                Map.of(
                        "result", 1,
                        "message", "회원가입 요청을 정상적으로 받았습니다."
                )// Map 객체를 만드는 문법
        );
    }

}
