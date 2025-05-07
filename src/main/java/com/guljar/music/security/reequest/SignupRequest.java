package com.guljar.music.security.reequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

        @NotBlank
        @Size(min = 3, max = 50)
        private String nameOfUser;

        @NotBlank
        @Size(min = 3, max = 20)
        private String username;

        @NotBlank
        @Email
        private String email;


        @NotBlank
        @Size(min = 6, max = 40)
        private String password;

}
