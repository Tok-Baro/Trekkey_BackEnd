package com.api.trekkey.global.security.jwt;

import com.api.trekkey.global.exception.CustomException;
import com.api.trekkey.global.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtExtractor jwtExtractor;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = jwtExtractor.extractAccessToken(request);

        if (StringUtils.hasText(token)
                && jwtTokenProvider.validateAccessToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (!accessTokenBlacklist.contains(token)) {
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (CustomException exception) {
                ErrorResponse<?> error = ErrorResponse.from(exception.getBaseResponseCode());
                response.setStatus(error.getHttpStatus());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), error);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
