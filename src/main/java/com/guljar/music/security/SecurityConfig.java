package com.guljar.music.security;

import com.guljar.music.security.jwt.AuthEntryPointJwt;
import com.guljar.music.security.jwt.AuthTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    @Lazy
    OAuthLoginSuccessHandler loginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {

        http.csrf(csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/public/**")
                        .ignoringRequestMatchers("/api/auth/oauth2/redirect")
                        .ignoringRequestMatchers("/api/auth/logout")
                        //.ignoringRequestMatchers("/api/image/process")
                        .ignoringRequestMatchers("/api/auth/me")
                        .ignoringRequestMatchers("/api/auth/public/signup")
                        .ignoringRequestMatchers("/api/auth/public/forgot-password")
        );

        http.authorizeHttpRequests((requests)
                        -> requests
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/api/csrf-token").permitAll()
                        .requestMatchers("/api/auth/me").permitAll()
                        .requestMatchers("/api/auth/public/**").permitAll()
                        .requestMatchers("/api/auth/public/forgot-password").permitAll()
                        .requestMatchers("api/auth/oauth2/redirect").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 ->{
                    oauth2.successHandler(loginSuccessHandler);

                });

        http.cors(cors -> cors.configurationSource(corsConfigurationSource));


        http.exceptionHandling(exception ->
                exception.authenticationEntryPoint(unauthorizedHandler));
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        //http.formLogin(withDefaults());
        //http.httpBasic(withDefaults());

        return http.build();
    }


    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception{
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
