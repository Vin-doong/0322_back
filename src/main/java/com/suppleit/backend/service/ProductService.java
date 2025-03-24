package com.suppleit.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suppleit.backend.dto.ProductDto;
import com.suppleit.backend.mapper.ProductMapper;
import com.suppleit.backend.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductMapper productMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${api.health-functional-food.url}")
    private String apiUrl;

    @Value("${api.health-functional-food.key}")
    private String apiKey;

    // 외부 API를 통한 제품 검색
    public List<ProductDto> searchProducts(String keyword) {
        log.info("제품 검색 시작: {}", keyword);
        List<ProductDto> results = new ArrayList<>();
        
        try {
            // 공공데이터 API 호출 URL 구성
            URI uri = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("serviceKey", apiKey)
                .queryParam("type", "json")
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", "20")
                .queryParam("prdlstNm", keyword) // 제품명 검색
                .build()
                .encode()
                .toUri();
            
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("API 요청 URL: {}", uri);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // JSON 파싱
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode items = root.path("body").path("items");
                
                // 검색 결과가 있는 경우
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        ProductDto productDto = parseProductFromJson(item);
                        
                        // DB에 저장하면서 동시에 결과 목록에 추가
                        saveProductToDb(productDto);
                        results.add(productDto);
                    }
                }
            }
            
            log.info("검색 결과 {}건 반환", results.size());
            return results;
        } catch (Exception e) {
            log.error("제품 검색 중 오류: {}", e.getMessage(), e);
            // 외부 API 오류 시 DB에서 검색
            return searchProductsFromDb(keyword);
        }
    }

    // DB에서 제품 검색
    private List<ProductDto> searchProductsFromDb(String keyword) {
        log.info("DB에서 제품 검색: {}", keyword);
        try {
            List<Product> products = productMapper.searchProducts(keyword);
            return products.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("DB 검색 중 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // 특정 제품 조회
    public ProductDto getProductById(Long productId) {
        log.info("제품 ID로 조회: {}", productId);
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new IllegalArgumentException("해당 제품을 찾을 수 없습니다: " + productId);
        }
        return convertToDto(product);
    }

    // JSON 응답에서 ProductDto 객체 생성
    private ProductDto parseProductFromJson(JsonNode item) {
        ProductDto dto = new ProductDto();
        
        // 필수 필드 설정
        dto.setPrdId(item.path("PRDLST_REPORT_NO").asLong(0));
        dto.setProductName(item.path("PRDLST_NM").asText(""));
        dto.setCompanyName(item.path("BSSH_NM").asText(""));
        
        // 선택적 필드 설정
        if (!item.path("POG_DAYCNT").isMissingNode()) {
            dto.setExpirationPeriod(item.path("POG_DAYCNT").asText(""));
        }
        if (!item.path("PRIMARY_FNCLTY").isMissingNode()) {
            dto.setMainFunction(item.path("PRIMARY_FNCLTY").asText(""));
        }
        if (!item.path("NTK_MTHD").isMissingNode()) {
            dto.setIntakeHint(item.path("NTK_MTHD").asText(""));
        }
        if (!item.path("PRSRV_PD").isMissingNode()) {
            dto.setPreservation(item.path("PRSRV_PD").asText(""));
        }
        if (!item.path("BASE_STANDARD").isMissingNode()) {
            dto.setBaseStandard(item.path("BASE_STANDARD").asText(""));
        }
        
        return dto;
    }

    // 제품 정보를 DB에 저장
    private void saveProductToDb(ProductDto productDto) {
        try {
            // 이미 존재하는지 확인
            Product existingProduct = productMapper.getProductById(productDto.getPrdId());
            
            if (existingProduct == null) {
                // 새로운 제품 저장
                Product product = convertToEntity(productDto);
                productMapper.insertProduct(product);
                log.info("새 제품 DB에 저장: {}", productDto.getProductName());
            } else {
                // 기존 제품 업데이트
                Product product = convertToEntity(productDto);
                productMapper.updateProduct(product);
                log.info("기존 제품 정보 업데이트: {}", productDto.getProductName());
            }
        } catch (Exception e) {
            log.error("제품 저장 중 오류: {}", e.getMessage(), e);
        }
    }

    // Entity -> DTO 변환
    private ProductDto convertToDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setPrdId(product.getPrdId());
        dto.setProductName(product.getProductName());
        dto.setCompanyName(product.getCompanyName());
        dto.setRegistrationNo(product.getRegistrationNo());
        dto.setExpirationPeriod(product.getExpirationPeriod());
        dto.setSrvUse(product.getSrvUse());
        dto.setMainFunction(product.getMainFunction());
        dto.setPreservation(product.getPreservation());
        dto.setIntakeHint(product.getIntakeHint());
        dto.setBaseStandard(product.getBaseStandard());
        return dto;
    }

    // DTO -> Entity 변환
    private Product convertToEntity(ProductDto dto) {
        Product product = new Product();
        product.setPrdId(dto.getPrdId());
        product.setProductName(dto.getProductName());
        product.setCompanyName(dto.getCompanyName());
        product.setRegistrationNo(dto.getRegistrationNo());
        product.setExpirationPeriod(dto.getExpirationPeriod());
        product.setSrvUse(dto.getSrvUse());
        product.setMainFunction(dto.getMainFunction());
        product.setPreservation(dto.getPreservation());
        product.setIntakeHint(dto.getIntakeHint());
        product.setBaseStandard(dto.getBaseStandard());
        return product;
    }
}