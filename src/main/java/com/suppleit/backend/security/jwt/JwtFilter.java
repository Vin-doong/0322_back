package com.suppleit.backend.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final JwtTokenBlacklistService tokenBlacklistService; // 추가

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 공지사항 조회 API는 인증 절차를 건너뛰도록 설정
            String requestUri = request.getRequestURI();
            // 수정: /api/notice로 시작하는 모든 GET 요청에 대해 인증 우회
            if (requestUri.startsWith("/api/notice") && request.getMethod().equals("GET")) {
                // 공지사항 조회는 인증 없이 진행
                chain.doFilter(request, response);
                return;
            }
            String token = resolveToken(request);

            if (token != null) {
                // 블랙리스트 토큰 확인 로직 추가
                if (tokenBlacklistService.isBlacklisted(token)) {
                    log.info("Token is blacklisted (logged out): {}", token.substring(0, 10) + "...");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been invalidated (logged out)");
                    return;
                }

                if (!jwtTokenProvider.validateToken(token)) {
                    log.warn("Invalid or expired token");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                    return;
                }
    
                String email = jwtTokenProvider.getEmail(token);
                
                // UserDetails 먼저 로드
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
    
                if (userDetails != null) {
                    // UserDetails의 기존 권한을 사용
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                        userDetails, 
                        null, 
                        userDetails.getAuthorities()  // 토큰에서 추출한 권한 대신 UserDetails의 권한 사용
                    );
                    
                    ((UsernamePasswordAuthenticationToken) auth).setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.warn("No user details found for email: {}", email);
                }
            }
    
            chain.doFilter(request, response);
    
        } catch (Exception e) {
            log.error("JWT Filter Error", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication error: " + e.getMessage());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null) {
            return null;
        }
        if (!bearerToken.startsWith("Bearer ")) {
            log.warn("Invalid token format: {}", bearerToken);
            throw new IllegalArgumentException("유효하지 않은 토큰 형식입니다.");
        }
        return bearerToken.substring(7);
    }
}