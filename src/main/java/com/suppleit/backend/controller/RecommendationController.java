package com.suppleit.backend.controller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suppleit.backend.dto.ProductResponse;
import com.suppleit.backend.service.RecommendationService;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class RecommendationController {

  private final RecommendationService recommendationService;
  private final ExecutorService executorService;

  @Value("${naver.api.client-id}")
  private String clientId;

  @Value("${naver.api.client-secret}")
  private String clientSecret;

  @Value("${naver.api.url}")
  private String naverApiUrl;

  @Value("${flask.api.url}")
  private String flaskUrl;

  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @GetMapping("api/recommend")
  public List<ProductResponse> getRecommendations(@RequestParam("keyword") String keyword) {
    log.info("Request received to get recommendations for keyword: {}", keyword);

    // 1. 원본 키워드로 직접 네이버 API 검색
    ProductResponse directResult = getNaverProductResponseWithFallback(keyword);
    List<ProductResponse> results = new ArrayList<>();
    if (directResult != null) {
      results.add(directResult);
    }

    // 2. Flask 서버에서 추천 키워드 받기
    List<String> recommendations = recommendationService.getRecommendations(keyword);

    if (recommendations.isEmpty()) {
      log.warn("No recommendations found for keyword: {}", keyword);
      return fillWithDummies(results, 9);
    }

    // 3. 추천 키워드로 검색하되 직접 네이버 검색 API 사용
    List<CompletableFuture<ProductResponse>> futures = new ArrayList<>();

    // 각 추천 키워드에 원본 키워드를 결합하여 검색 관련성 높이기
    for (String recommendation : recommendations) {
      String combinedQuery = recommendation + " " + keyword;
      futures.add(CompletableFuture.supplyAsync(
          () -> getNaverProductResponseWithFallback(combinedQuery),
          executorService).exceptionally(ex -> {
            log.error("Error occurred for query: {}, Exception: {}", combinedQuery, ex.getMessage());
            return null;
          }));
    }

    // 결과 수집
    List<ProductResponse> validProducts = futures.stream()
        .map(CompletableFuture::join)
        .filter(response -> response != null)
        .toList();

    log.info("Fetched {} valid products from Naver API", validProducts.size());
    results.addAll(validProducts);

    // 정확히 5개를 반환하기 위해 더미 데이터로 채우거나 잘라내기
    if (results.size() > 5) {
      return results.subList(0, 5);
    } else {
      return fillWithDummies(results, 5);
    }
  }

  /*
   * // Flask 서버로부터 POST 요청 처리를 위한 추가 메서드
   * 
   * @PostMapping("/recommend")
   * public List<ProductResponse> receiveRecommendations(@RequestBody
   * SearchRequest request) {
   * log.info("Received POST request with {} recommendations",
   * request.getProducts().size());
   * 
   * List<String> recommendations = request.getProducts();
   * if (recommendations.isEmpty()) {
   * log.warn("No recommendations received in POST request");
   * return fillWithDummies(new ArrayList<>(), 9);
   * }
   * 
   * // 네이버 API에서 추천 상품 검색
   * List<CompletableFuture<ProductResponse>> futures = new ArrayList<>();
   * for (String recommendation : recommendations) {
   * futures.add(CompletableFuture.supplyAsync(
   * () -> getNaverProductResponseWithFallback(recommendation),
   * executorService).exceptionally(ex -> {
   * log.error("Error occurred for query: {}, Exception: {}", recommendation,
   * ex.getMessage());
   * return null;
   * }));
   * }
   * 
   * List<ProductResponse> validProducts = futures.stream()
   * .map(CompletableFuture::join)
   * .filter(response -> response != null)
   * .toList();
   * 
   * log.info("Fetched {} valid products from Naver API", validProducts.size());
   * 
   * // 정확히 9개를 반환하기 위해 더미 데이터로 채우기
   * return fillWithDummies(validProducts, 8);
   * }
   */

  private ProductResponse getNaverProductResponse(String query) {
    log.debug("Searching for product on Naver with query: {}", query);
    try {
      URI naverUri = UriComponentsBuilder.fromUriString(naverApiUrl)
          .queryParam("query", query)
          .build().encode().toUri();

      HttpHeaders headers = new HttpHeaders();
      headers.set("X-Naver-Client-Id", clientId);
      headers.set("X-Naver-Client-Secret", clientSecret);

      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response = new RestTemplate().exchange(naverUri, HttpMethod.GET, entity, String.class);

      if (response.getBody() == null) {
        log.warn("No response body received for query: {}", query);
        return null;
      }

      JsonNode root = new ObjectMapper().readTree(response.getBody());
      JsonNode items = root.path("items");

      if (items.isArray() && items.size() > 0) {
        JsonNode item = items.get(0);
        log.info("Found product: {} with price: {}", item.path("title").asText(), item.path("lprice").asInt(0));

        return new ProductResponse(
            item.path("title").asText(),
            item.path("link").asText(),
            item.path("image").asText(),
            item.path("lprice").asInt(0),
            false);
      } else {
        log.warn("No items found for query: {}", query);
      }
    } catch (Exception e) {
      log.error("Error occurred while processing query: {}, Exception: {}", query, e.getMessage());
    }

    return null;
  }

  private ProductResponse getNaverProductResponseWithFallback(String query) {
    // 기존 getNaverProductResponse를 최적화한 버전
    log.debug("Searching for product on Naver with query: {}", query);
    try {
      // 네이버 API 호출 전 짧은 지연 추가 (속도 제한 방지)
      Thread.sleep(300); // 300ms 지연
      // 쿼리 최적화 (특수문자 제거, 키워드 정리 등)
      String optimizedQuery = optimizeSearchQuery(query);

      URI naverUri = UriComponentsBuilder.fromUriString(naverApiUrl)
          .queryParam("query", optimizedQuery)
          .queryParam("display", 5) // 여러 결과를 가져와서 최적의 결과 선택
          .build().encode().toUri();

      HttpHeaders headers = new HttpHeaders();
      headers.set("X-Naver-Client-Id", clientId);
      headers.set("X-Naver-Client-Secret", clientSecret);

      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response = new RestTemplate().exchange(naverUri, HttpMethod.GET, entity, String.class);

      if (response.getBody() == null) {
        log.warn("No response body received for query: {}", optimizedQuery);
        return null;
      }

      JsonNode root = new ObjectMapper().readTree(response.getBody());
      JsonNode items = root.path("items");

      if (items.isArray() && items.size() > 0) {
        // 최적의 결과 선택
        JsonNode bestItem = findBestMatch(items, query);

        if (bestItem != null) {
          log.info("Found product: {} with price: {}", bestItem.path("title").asText(),
              bestItem.path("lprice").asInt(0));

          return new ProductResponse(
              bestItem.path("title").asText(),
              bestItem.path("link").asText(),
              bestItem.path("image").asText(),
              bestItem.path("lprice").asInt(0),
              false); // 실제 상품이므로 isDummy = false
        }
      } else {
        log.warn("No items found for query: {}", optimizedQuery);

        // 대체 쿼리 시도 (키워드 단순화)
        if (optimizedQuery.contains(" ")) {
          String simplifiedQuery = optimizedQuery.split(" ")[0]; // 첫번째 단어만 사용
          log.info("Trying simplified query: {}", simplifiedQuery);
          return getNaverProductResponse(simplifiedQuery);
        }
      }
    } catch (Exception e) {
      log.error("Error occurred while processing query: {}, Exception: {}", query, e.getMessage());
    }

    return null;
  }

  // 더미 상품으로 채우는 헬퍼 메소드
  private List<ProductResponse> fillWithDummies(List<ProductResponse> products, int targetSize) {
    List<ProductResponse> result = new ArrayList<>(products);
    for (int i = products.size(); i < targetSize; i++) {
      result.add(createDummyProduct(i));
    }
    return result;
  }

  // 더미 상품 생성 메서드
  private ProductResponse createDummyProduct(int index) {
    return new ProductResponse(
        "추천 준비 중", // 제목
        "#", // 링크
        "#", // 더미 이미지 경로
        0, // 가격
        true // 더미 표시 플래그
    );
  }

  // 쿼리 최적화 메소드
  private String optimizeSearchQuery(String query) {
    // 괄호와 특수문자 제거
    return query.replaceAll("[\\(\\)\\[\\]\\{\\}]", "").trim();
  }

  // 가장 적합한 결과 찾기
  private JsonNode findBestMatch(JsonNode items, String originalQuery) {
    JsonNode bestItem = null;
    int highestScore = -1;

    for (JsonNode item : items) {
      String title = item.path("title").asText();
      // HTML 태그 제거
      String cleanTitle = title.replaceAll("<[^>]*>", "");
      // 간단한 관련성 점수 계산
      int score = calculateRelevanceScore(originalQuery, cleanTitle);

      if (score > highestScore) {
        highestScore = score;
        bestItem = item;
      }
    }

    return bestItem;
  }

  // 관련성 점수 계산
  private int calculateRelevanceScore(String query, String title) {
    int score = 0;
    String lowerQuery = query.toLowerCase();
    String lowerTitle = title.toLowerCase();

    // 전체 쿼리가 제목에 포함되면 높은 점수
    if (lowerTitle.contains(lowerQuery)) {
      score += 100;
    }

    // 개별 단어 일치 점수
    String[] queryWords = lowerQuery.split("\\s+");
    for (String word : queryWords) {
      if (word.length() > 1 && lowerTitle.contains(word)) {
        score += 10;
      }
    }

    return score;
  }
}