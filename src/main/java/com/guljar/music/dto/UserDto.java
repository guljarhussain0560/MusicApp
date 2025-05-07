package com.guljar.music.dto;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


@Setter
@Data
@RequiredArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String profileImageUrl;
}
