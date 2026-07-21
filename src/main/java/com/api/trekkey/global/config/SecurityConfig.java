package com.api.trekkey.global.config;

import com.api.trekkey.global.security.handler.JwtAccessDeniedHandler;
import com.api.trekkey.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.trekkey.global.security.jwt.JwtAuthenticationFilter;
import com.api.trekkey.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] SWAGGER_URLS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_URLS).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/organizations/**").permitAll() //회원가입 시 학교 검색 API
                        .requestMatchers(HttpMethod.GET, "/api/contests").hasRole("PARTICIPANT")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
