package com.guljar.music.security;

import com.guljar.music.model.User;
import com.guljar.music.security.jwt.JwtUtils;
import com.guljar.music.security.service.UserDetailsImpl;
import com.guljar.music.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private final UserService userService;

    @Autowired
    private final JwtUtils jwtUtils;

    @Value("${frontend.url}")
    private String frontendUrl;

    String username;
    String profileImageUrl;

    String idAttributeKey;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) authentication;
        if ("github".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId()) || "google".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
            DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = principal.getAttributes();
            String email = attributes.getOrDefault("email", "").toString();
            String name = attributes.getOrDefault("name", "").toString();

            if ("github".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
                username = attributes.getOrDefault("login", "").toString();
                profileImageUrl = attributes.getOrDefault("avatar_url", "").toString();
                idAttributeKey = "id";
            } else if ("google".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
                profileImageUrl = attributes.getOrDefault("picture", "").toString();
                username = email.split("@")[0];
                idAttributeKey = "sub";
            } else {
                username = "";
                idAttributeKey = "id";
            }
            System.out.println("HELLO OAUTH: " + email + " : " + name + " : " + username);

            userService.findByEmail(email)
                    .ifPresentOrElse(user -> {
                        DefaultOAuth2User oauthUser = new DefaultOAuth2User(
                                List.of(),
                                attributes,
                                idAttributeKey
                        );
                        Authentication securityAuth = new OAuth2AuthenticationToken(
                                oauthUser,
                                List.of(),
                                oAuth2AuthenticationToken.getAuthorizedClientRegistrationId()
                        );
                        SecurityContextHolder.getContext().setAuthentication(securityAuth);
                    }, () -> {
                        User newUser = new User();

                        newUser.setEmail(email);
                        newUser.setUserName(username);
                        newUser.setNameOfUser(name);
                        newUser.setProfilePictureUrl(profileImageUrl);
                        newUser.setSignUpMethod(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId());
                        userService.registerUser(newUser);
                        DefaultOAuth2User oauthUser = new DefaultOAuth2User(
                                List.of(),
                                attributes,
                                idAttributeKey
                        );
                        Authentication securityAuth = new OAuth2AuthenticationToken(
                                oauthUser,
                                List.of(),
                                oAuth2AuthenticationToken.getAuthorizedClientRegistrationId()
                        );
                        SecurityContextHolder.getContext().setAuthentication(securityAuth);
                    });
        }
        this.setAlwaysUseDefaultTargetUrl(true);

        // JWT TOKEN LOGIC
        DefaultOAuth2User oauth2User = (DefaultOAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();

        // Extract necessary attributes
        String email = (String) attributes.get("email");
        System.out.println("OAuth2LoginSuccessHandler: " + username + " : " + email);


        // You can skip this part or just keep empty authorities if needed
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        User user = userService.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        // Create UserDetailsImpl instance
        UserDetailsImpl userDetails = new UserDetailsImpl(
                null,
                username,
                email,
                null,
                null,
                authorities.toString()
        );

        // Generate JWT token
        String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);

        // Redirect to the frontend with the JWT token
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("token", jwtToken)
                .build().toUriString();
        this.setDefaultTargetUrl(targetUrl);
        super.onAuthenticationSuccess(request, response, authentication);
    }

}
