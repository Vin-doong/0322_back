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
import java.net.URLEncoder;
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
            // 공공데이터 API 호출 URL 구성 - 요청 변수에 맞게 수정
            URI uri = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("serviceKey", apiKey)
                .queryParam("Prduct", keyword) // 제품명 검색 (Prduct)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 10)
                .queryParam("type", "json") // xml 대신 json으로 요청
                .build()
                .encode()
                .toUri();
            
            log.info("API 요청 URL: {}", uri);
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // JSON 파싱
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // API 응답 구조 확인
                JsonNode body = root.path("body");
                JsonNode items = body.path("items");
                
                if (items.isArray() && items.size() > 0) {
                    for (JsonNode item : items) {
                        ProductDto productDto = parseProductFromJson(item);
                        
                        // DB에 저장하면서 동시에 결과 목록에 추가
                        saveProductToDb(productDto);
                        results.add(productDto);
                    }
                    log.info("API 검색 결과 {}건 반환", results.size());
                } else {
                    log.info("API 검색 결과 없음, DB 검색으로 전환");
                    return searchProductsFromDb(keyword);
                }
            } else {
                log.warn("API 응답 오류 또는 응답 없음");
                return searchProductsFromDb(keyword);
            }
            
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
        
        try {
            // 로깅을 통해 실제 응답 구조 파악
            log.debug("Item JSON 구조: {}", item.toString());
            
            // 응답 필드에 맞게 데이터 추출
            String prdlstReportNo = item.path("PRDLST_REPORT_NO").asText("");
            String prdlstNm = item.path("PRDLST_NM").asText("");
            String bsshNm = item.path("BSSH_NM").asText("");
            
            // 제품 ID 생성
            Long prdId;
            try {
                // 등록번호가 숫자인 경우
                prdId = Long.parseLong(prdlstReportNo.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                // 등록번호가 숫자가 아니거나 비어있는 경우 해시 기반 임시 ID 생성
                prdId = Math.abs((prdlstNm.hashCode() + System.currentTimeMillis()) % 1000000000L);
            }
            
            dto.setPrdId(prdId);
            dto.setProductName(prdlstNm);
            dto.setCompanyName(bsshNm);
            dto.setRegistrationNo(prdlstReportNo);
            
            // 기타 필드들 (존재한다면)
            if (item.has("POG_DAYCNT")) {
                dto.setExpirationPeriod(item.path("POG_DAYCNT").asText(""));
            }
            if (item.has("PRIMARY_FNCLTY")) {
                dto.setMainFunction(item.path("PRIMARY_FNCLTY").asText(""));
            }
            if (item.has("NTK_MTHD")) {
                dto.setIntakeHint(item.path("NTK_MTHD").asText(""));
            }
            if (item.has("PRSRV_PD")) {
                dto.setPreservation(item.path("PRSRV_PD").asText(""));
            }
            if (item.has("BASE_STANDARD")) {
                dto.setBaseStandard(item.path("BASE_STANDARD").asText(""));
            }
        } catch (Exception e) {
            log.error("제품 데이터 파싱 중 오류: {}", e.getMessage(), e);
        }
        
        return dto;
    }

    // 노드에서 텍스트 안전하게 가져오기
    private String getTextFromNode(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode()) {
            return "";
        }
        return node.path(fieldName).asText("");
    }

    // 임시 ID 생성
    private Long generateTempId(String key) {
        return Math.abs((key.hashCode() + System.currentTimeMillis()) % 1000000000L);
    }

    // 제품 정보를 DB에 저장
    private void saveProductToDb(ProductDto productDto) {
        try {
            // ID가 0인 경우 처리
            if (productDto.getPrdId() == 0) {
                log.warn("제품 ID가 0이므로 저장하지 않음: {}", productDto.getProductName());
                return;
            }
            
            // 이미 존재하는지 확인
            Product existingProduct = productMapper.getProductById(productDto.getPrdId());
            
            if (existingProduct == null) {
                // 새로운 제품 저장
                Product product = convertToEntity(productDto);
                productMapper.insertProduct(product);
                log.info("새 제품 DB에 저장: {}", productDto.getProductName());
            } else {
                // 기존 제품 업데이트 - API에서 가져온 데이터로 갱신
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