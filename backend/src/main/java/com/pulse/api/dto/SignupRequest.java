package com.pulse.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    // React에서 보내는 JSON을 여기 DTO가 받음
    private String email;
    private String password;
}
