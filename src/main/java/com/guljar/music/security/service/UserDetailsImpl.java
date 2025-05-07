package com.guljar.music.security.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.guljar.music.model.User;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor
@Data

@Setter
@Getter
public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String nameOfUser;
    private String profileImageUrl;

    @JsonIgnore
    private String password;

    private Collection<? extends GrantedAuthority> authorities;




    public UserDetailsImpl(Long id, String username,String nameOfUser, String email, String password,String profileImageUrl) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.nameOfUser = nameOfUser;
        this.profileImageUrl = profileImageUrl;

    }

    public static UserDetailsImpl build(User user) {
        return new UserDetailsImpl(
                user.getUserId(),
                user.getUserName(),
                user.getNameOfUser(),
                user.getEmail(),
                user.getPassword(),
                user.getProfilePictureUrl()
        );

    }



    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserDetailsImpl user = (UserDetailsImpl) o;
        return id.equals(user.id);
    }
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, password);
    }

}
