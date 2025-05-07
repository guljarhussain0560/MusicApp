package com.guljar.music.security.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class LoginResponse {
    private String jwtToken;

    private String username;


    public LoginResponse(String username, String jwtToken) {
        this.username = username;
        this.jwtToken = jwtToken;
    }

}
