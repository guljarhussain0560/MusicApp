package com.guljar.music.service;

import com.guljar.music.model.PasswordResetToken;
import com.guljar.music.model.User;
import com.guljar.music.repo.PasswordResetTokenRepository;
import com.guljar.music.repo.UserRepository;
import com.guljar.music.utils.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    @Value("${frontend.url}")
    String frontendUrl;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    EmailService emailService;

    public User findByUsername(String username) {
        Optional<User> user = userRepository.findByUserName(username);
        return user.orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    public void updatePassword(Long userId, String password) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update password");
        }

    }

    public List<User> getAllUsers(){
        return userRepository.findAll();
    }

    public void generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plus(24, ChronoUnit.HOURS);
        PasswordResetToken passwordResetToken = new PasswordResetToken(token, user, expiryDate);

        passwordResetTokenRepository.save(passwordResetToken);


        String resetURL = frontendUrl+"/reset-password?token=" + token;

        // Send email with reset URL
        emailService.sendPasswordResetEmail(email, resetURL);

    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        if (resetToken.isUsed()){
            throw new RuntimeException("Password reset Token is used");
        }
        if (resetToken.getExpiryDate().isBefore(Instant.now())){
            throw new RuntimeException("Password reset Token is expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User registerUser(User newUser) {
        if(newUser.getPassword() != null){
            newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        }
        return userRepository.save(newUser);

    }



}
