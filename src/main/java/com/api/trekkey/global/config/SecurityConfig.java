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
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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

    /**
     * ROOT_ADMIN은 ADMIN 권한을 포함한다 (docs/ADMIN_SECURITY.md §2-1).
     * 이 빈은 @EnableMethodSecurity(@PreAuthorize)와 authorizeHttpRequests 양쪽에 자동 적용되므로
     * 기존 hasRole("ADMIN") 검사를 ROOT_ADMIN이 수정 없이 통과한다.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ROOT_ADMIN > ROLE_ADMIN");
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
                        .requestMatchers(HttpMethod.POST, "/api/review/access").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/review/assignments").permitAll()
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/review/assignments/*/review"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/review/files/*/download/check"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/review/files/*/download"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/contests").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/contests/*").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.POST, "/api/contests/*/applications").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.POST, "/api/contests/*/like").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/me/applications").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/me/applications/*/progress")
                        .hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/me/applications/*").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/me/teams").hasRole("PARTICIPANT")
                        .requestMatchers("/api/me/graduation/**").hasRole("PARTICIPANT")
                        .requestMatchers("/api/me/evidence-submissions/**").hasRole("PARTICIPANT")
                        .requestMatchers("/api/me/evidence-files/**").hasRole("PARTICIPANT")
                        .requestMatchers(HttpMethod.GET, "/api/participants/search").hasRole("PARTICIPANT")
                        .requestMatchers(
                                "/api/teams/*/submission",
                                "/api/teams/*/submission/**"
                        ).hasRole("PARTICIPANT")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/files/*/download"
                        ).hasRole("PARTICIPANT")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") //관리자 콘솔 (RoleHierarchy로 ROOT 포함)
                        .requestMatchers("/api/root/**").hasRole("ROOT_ADMIN") //초대 발급·가입 승인 등 ROOT_ADMIN 전용
                        .requestMatchers(HttpMethod.GET, "/api/public/credentials/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/activity-profiles/**").permitAll()
                        .requestMatchers("/api/admin/blockchain/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
