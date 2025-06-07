package com.guljar.music.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.guljar.music.dto.ForgotPasswordRequest;
import com.guljar.music.model.User;
import com.guljar.music.repo.UserRepository;
import com.guljar.music.security.reequest.LoginRequest;
import com.guljar.music.security.reequest.SignupRequest;
import com.guljar.music.security.response.LoginResponse;
import com.guljar.music.security.response.MessageResponse;
import com.guljar.music.security.response.UserInfoResponse;
import com.guljar.music.security.jwt.JwtUtils;
import com.guljar.music.security.service.UserDetailsImpl;
import com.guljar.music.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserService userService;


    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Map<String, Object> user = new HashMap<>();
        user.put("username", userDetails.getUsername());
        user.put("email", userDetails.getEmail());
        user.put("name", userDetails.getNameOfUser());
        user.put("profilePictureUrl", userDetails.getProfileImageUrl());


        return ResponseEntity.ok(user);
    }



    @PostMapping("/public/signin")
    public ResponseEntity<?>  authenticateUser( @RequestBody LoginRequest loginRequest) {

        Authentication authentication;
        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        }catch (AuthenticationException e){
            Map<String,Object> map = new HashMap<>();
            map.put("message","Invalid username or password");
            map.put("status",false);
            return new ResponseEntity<>(map, HttpStatus.NOT_FOUND);
        }

        //set authentication
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);

        //prepare response
        LoginResponse response = new LoginResponse(userDetails.getUsername(), jwtToken);
        return ResponseEntity.ok(response);

    }


    @PostMapping("/public/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        if (userRepository.existsByUserName((signupRequest.getUsername()))){
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail((signupRequest.getEmail()))){
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User(signupRequest.getNameOfUser(), signupRequest.getUsername(), signupRequest.getEmail(), passwordEncoder.encode(signupRequest.getPassword()));

        System.out.println("name of user: "+signupRequest.getNameOfUser());

        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));

    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserDetails(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        UserInfoResponse response = new UserInfoResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getNameOfUser(),
                user.getProfilePictureUrl()
        );

        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/username")
    public ResponseEntity<?> getUsername(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().body(userDetails.getUsername());
    }
    //reset forgot password

    @PostMapping("/public/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail();
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email cannot be empty"));
        }
        try{
            userService.generatePasswordResetToken(email);
            return ResponseEntity.ok().body(new MessageResponse("Password reset email sent successfully"));
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error sending password reset email"));
        }

    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token,@RequestParam String newPassword) {
        try{
            userService.resetPassword(token,newPassword);
            return ResponseEntity.ok().body(new MessageResponse("Password reset successfully"));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(new MessageResponse("Error resetting password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(new MessageResponse("User logged out successfully"));
    }


}
