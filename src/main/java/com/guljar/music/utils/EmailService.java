package com.guljar.music.utils;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    public void sendPasswordResetEmail(String to , String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        //message.setFrom("guljarhussain7865@gmail.com");
        message.setTo(to);

        message.setSubject("Password Reset Request ");
        message.setText("To reset your password, click the link below:\n" + resetUrl);
        try {
            javaMailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error sending email: " + e.getMessage());
            // Handle the exception as needed
        }

    }



}
