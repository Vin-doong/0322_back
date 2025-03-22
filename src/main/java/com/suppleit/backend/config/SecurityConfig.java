package com.suppleit.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suppleit.backend.security.jwt.JwtFilter;
import com.suppleit.backend.security.jwt.JwtTokenProvider;
import com.suppleit.backend.security.jwt.JwtTokenBlacklistService;
import com.suppleit.backend.service.MemberDetailsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberDetailsService memberDetailsService;
    private final JwtTokenBlacklistService tokenBlacklistService; // 추가

    @Bean
    public OAuth2LoginConfigurer<HttpSecurity> oauth2LoginConfigurer() {
        return new OAuth2LoginConfigurer<HttpSecurity>()
            .successHandler((request, response, authentication) -> {
                // 로그인 성공 후 처리 로직
            })
            .failureHandler((request, response, exception) -> {
                // 로그인 실패 처리 로직
                log.error("OAuth2 Login 실패: {}", exception.getMessage());
            });
    }

    // ✅ 비밀번호 암호화 (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ✅ 보안 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // CSRF 보호 비활성화 (REST API)
            .cors(cors -> cors.configurationSource(corsSource()))  // CORS 설정 활성화
            .formLogin(form -> form.disable())  // 기본 로그인 폼 비활성화
            .httpBasic(basic -> basic.disable())  // HTTP Basic 인증 비활성화
            .authorizeHttpRequests(this::configureAuthorization)  // 요청별 권한 설정
            .addFilterBefore(jwtFilter(), 
                    UsernamePasswordAuthenticationFilter.class)  // ✅ JWT 필터 적용
            .logout(this::configureLogout);  // 로그아웃 설정

        return http.build();
    }
    
    // ✅ JWT 필터를 Bean으로 등록
    @Bean
    public JwtFilter jwtFilter() {
        return new JwtFilter(jwtTokenProvider, memberDetailsService, tokenBlacklistService);
    }

    // ✅ 요청별 권한 설정
    private void configureAuthorization(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
            .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")  // ✅ 관리자 권한 필요
            .requestMatchers("/api/member/auth/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_USER")  // ✅ 관리자 & 사용자 권한 필요
            .requestMatchers("/api/logout").authenticated() // ✅ 로그인한 사용자만 로그아웃 가능

            // 소셜 로그인 API는 인증 없이 접근 가능
            .requestMatchers("/api/social/login/**").permitAll()

            // 추가: 이메일 인증과 토큰 갱신은 인증 없이 접근 가능
            .requestMatchers("/api/member/verify-email").permitAll()
            .requestMatchers("/api/auth/refresh").permitAll()
            .requestMatchers("/api/auth/login").permitAll()

            //공지사항
            .requestMatchers("/api/notice/image/**").permitAll()  // 이미지 접근 허용
            .requestMatchers("/api/notice/attachment/**").permitAll()  // 첨부파일 접근 허용

            .requestMatchers(HttpMethod.GET, "/api/notice/**").permitAll()  // 모든 사용자 공지사항 조회 가능
            .requestMatchers(HttpMethod.POST, "/api/notice").hasAuthority("ROLE_ADMIN")  // 공지사항 작성은 관리자만
            .requestMatchers(HttpMethod.PUT, "/api/notice/**").hasAuthority("ROLE_ADMIN")  // 공지사항 수정은 관리자만
            .requestMatchers(HttpMethod.DELETE, "/api/notice/**").hasAuthority("ROLE_ADMIN")  // 공지사항 삭제는 관리자만

            .anyRequest().permitAll();  // ✅ 그 외 요청은 누구나 가능
    }

    // ✅ 로그아웃 설정 추가
    private void configureLogout(LogoutConfigurer<HttpSecurity> logout) {
        logout
            .logoutUrl("/api/logout")
            .logoutSuccessHandler((request, response, authentication) -> {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("{\"message\": \"Logout successful\"}");
                response.getWriter().flush();
            });
    }

    // ✅ CORS 설정 보완 (특정 도메인만 허용)
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowCredentials(true);  // 인증 정보(쿠키, 세션) 포함 허용
        corsConfig.addAllowedHeader("*");  // 모든 HTTP 헤더 허용
        corsConfig.addAllowedMethod("*");  // 모든 HTTP 메서드 허용
        corsConfig.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://localhost:5000"));  // ✅ 특정 도메인만 허용 (보안 강화)
        corsConfig.addExposedHeader("Authorization");  // 클라이언트에서 Authorization 헤더 접근 가능

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}