package com.suppleit.backend.security.jwt;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class JwtTokenBlacklistService {
    
    // 메모리 기반 블랙리스트 (토큰 -> 만료 시간)
    private final Map<String, Long> tokenBlacklist = new ConcurrentHashMap<>();
    
    // 토큰을 블랙리스트에 추가
    public void addToBlacklist(String token, Long expiryTimeInMillis) {
        log.info("Adding token to blacklist, expires at: {}", expiryTimeInMillis);
        tokenBlacklist.put(token, expiryTimeInMillis);
        
        // 만료된 토큰 정리 (선택적)
        cleanupExpiredTokens();
    }
    
    // 토큰이 블랙리스트에 있는지 확인
    public boolean isBlacklisted(String token) {
        boolean isBlacklisted = tokenBlacklist.containsKey(token);
        if (isBlacklisted) {
            log.info("Token is in blacklist");
        }
        return isBlacklisted;
    }
    
    // 만료된 토큰 정리 (선택적)
    private void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        int beforeSize = tokenBlacklist.size();
        
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        
        int afterSize = tokenBlacklist.size();
        if (beforeSize > afterSize) {
            log.info("Cleaned up {} expired tokens from blacklist", beforeSize - afterSize);
        }
    }
}