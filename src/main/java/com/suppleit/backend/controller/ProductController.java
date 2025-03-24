// src/main/java/com/suppleit/backend/controller/ProductController.java
package com.suppleit.backend.controller;

import com.suppleit.backend.dto.ApiResponse;
import com.suppleit.backend.dto.ProductDto;
import com.suppleit.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestParam String keyword) {
        log.info("제품 검색 요청: {}", keyword);
        try {
            List<ProductDto> products = productService.searchProducts(keyword);
            return ResponseEntity.ok(ApiResponse.success("검색 성공", products));
        } catch (Exception e) {
            log.error("제품 검색 중 오류: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductById(@PathVariable Long productId) {
        log.info("제품 상세 조회 요청: {}", productId);
        try {
            ProductDto product = productService.getProductById(productId);
            return ResponseEntity.ok(ApiResponse.success("조회 성공", product));
        } catch (Exception e) {
            log.error("제품 조회 중 오류: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("제품 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}