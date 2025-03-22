# supple-it-backend

backend/
│── src/
│   ├── main/
│   │   ├── java/com/suppleit/backend/
│   │   │   ├── controller/   # API 엔드포인트 (RestController)
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── MemberController.java
│   │   │   │   ├── SocialLoginController.java
│   │   │   ├── service/      # 비즈니스 로직 (Service Layer)
│   │   │   │   ├── MemberService.java
│   │   │   │   ├── SocialLoginService.java
│   │   │   │   ├── MemberDetailsService.java  # Spring Security 인증
│   │   │   ├── model/        # 엔티티 클래스 (DB 매핑)
│   │   │   │   ├── Member.java
│   │   │   ├── mapper/       # MyBatis Mapper (SQL 인터페이스)
│   │   │   │   ├── MemberMapper.java
│   │   │   ├── security/     # Spring Security & JWT
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── JwtFilter.java
│   │   │   ├── constants/    # 상수, Enum 정의
│   │   │   │   ├── MemberRole.java  # 사용자 역할 (USER, ADMIN)
│   │   │   │   ├── SocialType.java  # 로그인 유형 (NONE, KAKAO, NAVER, GOOGLE)
│   │   │   │   ├── Gender.java  # 성별 (MALE, FEMALE)
│   │   │   ├── dto/          # DTO 클래스 (데이터 전송 객체)
│   │   │   │   ├── MemberDto.java
│   │   │   │   ├── AuthRequest.java  # 로그인 요청 데이터
│   │   ├── resources/        # 설정 파일 & SQL 매퍼
│   │   │   ├── application.yml  # Spring Boot 설정 파일
│   │   │   ├── mapper/       # MyBatis XML 매퍼
│   │   │   │   ├── MemberMapper.xml
│   │── test/                 # 테스트 코드
│── BackendApplication.java   # Spring Boot 애플리케이션 실행 파일
│── build.gradle            # Gradle 설정 파일