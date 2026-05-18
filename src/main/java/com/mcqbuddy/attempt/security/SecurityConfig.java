package com.mcqbuddy.attempt.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.PUT, "/attempt-api/attempts/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/attempt-api/attempts/exams/*/import-marking-scheme").permitAll()
                        .requestMatchers(HttpMethod.POST, "/attempt-api/attempts/exams/*/start").permitAll()
                        .requestMatchers(HttpMethod.POST, "/attempt-api/attempts/answer-selections").permitAll()
                        .requestMatchers(HttpMethod.POST, "/attempt-api/attempts/*/finish").permitAll()
                        .requestMatchers("/attempt-api/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN")
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
