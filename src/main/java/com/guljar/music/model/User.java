package com.guljar.music.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Table (name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @NotBlank
    @Size(max = 20)
    @Column(name = "username")
    private String userName;

    @Size(max = 120)
    @Column(name = "password")
    @JsonIgnore
    private String password;

    @NotBlank
    @Column(name = "name")
    @Size(min = 6, max = 40)
    private String nameOfUser;


    @Column( nullable = false, unique = true)
    private String email;


    private String profilePictureUrl;
    private String signUpMethod;

    public User(String nameOfUser,String userName, String email, String password) {
        this.nameOfUser = nameOfUser;
        this.userName = userName;
        this.email = email;
        this.password = password;
    }

    public User(String userName,String password)
    {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return userId != null && userId.equals(((User) o).getUserId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }


}
