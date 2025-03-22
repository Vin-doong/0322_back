-- 회원 테이블
CREATE TABLE Member (
  member_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '회원 고유 ID',
  email VARCHAR(200) NOT NULL UNIQUE COMMENT '로컬/소셜 로그인 이메일',
  password VARCHAR(200) NULL COMMENT '로컬 로그인은 필수, 소셜 로그인 사용자는 NULL',
  nickname VARCHAR(50) NULL COMMENT '닉네임',
  gender VARCHAR(10) NULL COMMENT '성별 (MALE/FEMALE, 소셜 로그인에 따라 NULL 허용)',
  birth DATE NULL COMMENT '생년월일 (소셜 로그인에 따라 NULL 허용)',
  member_role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '사용자 역할',
  social_type VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '소셜 로그인 유형',

  CONSTRAINT chk_member_role CHECK (member_role IN ('USER', 'ADMIN')),
  CONSTRAINT chk_social_type CHECK (social_type IN ('NONE', 'KAKAO', 'NAVER', 'GOOGLE'))
);

-- 토큰 블랙리스트 테이블 (Redis 대체용)reviewimage
CREATE TABLE TokenBlacklist (
  token_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 ID',
  token_value VARCHAR(255) NOT NULL UNIQUE COMMENT '토큰 값',
  expiry_date TIMESTAMP NOT NULL COMMENT '만료 시간',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시간',
  INDEX idx_expiry_date (expiry_date),
  INDEX idx_token_value (token_value)
);

-- 제품 테이블
CREATE TABLE Product (
  prd_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_name VARCHAR(50) NOT NULL COMMENT '제품명',
  company_name VARCHAR(50) NOT NULL COMMENT '제조사',
  registration_no VARCHAR(200) NULL COMMENT '등록번호',
  expiration_period VARCHAR(50) NULL COMMENT '유통기한',
  srv_use TEXT NULL COMMENT '사용법',
  main_function TEXT NULL COMMENT '주요 기능',
  preservation TEXT NULL COMMENT '보존 방법',
  intake_hint TEXT NULL COMMENT '섭취 힌트',
  base_standard TEXT NULL COMMENT '기본 기준'
);

-- 즐겨찾기 테이블
CREATE TABLE Favorite (
  favorite_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL COMMENT '회원 ID',
  prd_id BIGINT NOT NULL COMMENT '제품 ID',

  FOREIGN KEY (member_id) REFERENCES Member(member_id) ON DELETE CASCADE,
  FOREIGN KEY (prd_id) REFERENCES Product(prd_id) ON DELETE CASCADE,
  UNIQUE KEY (member_id, prd_id) COMMENT '같은 제품 중복 즐겨찾기 방지'
);

-- 복용 스케줄 테이블
CREATE TABLE Schedule (
  schedule_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL COMMENT '회원 ID',
  prd_id BIGINT NOT NULL COMMENT '제품 ID',
  intake_start DATE NOT NULL COMMENT '복용 시작일',
  intake_distance INT NULL COMMENT '복용 간격(일)',
  intake_end DATE NULL COMMENT '복용 종료일',
  memo TEXT NULL COMMENT '메모',

  FOREIGN KEY (member_id) REFERENCES Member(member_id) ON DELETE CASCADE,
  FOREIGN KEY (prd_id) REFERENCES Product(prd_id) ON DELETE CASCADE
);

-- 공지사항 테이블 (이미지, 첨부파일 기능 포함)
CREATE TABLE Notice (
  notice_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL COMMENT '공지 제목',
  content LONGTEXT NOT NULL COMMENT '공지 내용',
  member_id BIGINT NOT NULL COMMENT '작성자 ID',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
  updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정일',
  views INT NOT NULL DEFAULT 0 COMMENT '조회수',
  last_modified_by BIGINT NULL COMMENT '마지막 수정자 ID',
  image_path VARCHAR(255) NULL COMMENT '이미지 경로',
  attachment_path VARCHAR(255) NULL COMMENT '첨부파일 경로',
  attachment_name VARCHAR(255) NULL COMMENT '첨부파일 원본명',
  
  FOREIGN KEY (member_id) REFERENCES Member(member_id) ON DELETE CASCADE,
  FOREIGN KEY (last_modified_by) REFERENCES Member(member_id) ON DELETE SET NULL
);

-- 리뷰 테이블
CREATE TABLE Review (
  review_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL COMMENT '회원 ID',
  prd_id BIGINT NOT NULL COMMENT '제품 ID',
  views INT NOT NULL DEFAULT 0 COMMENT '조회수',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
  updated_at TIMESTAMP NULL DEFAULT NULL COMMENT '수정일',
  rating VARCHAR(20) NULL COMMENT '평가(LIKE/DISLIKE/NEUTRAL)',
  content LONGTEXT NULL COMMENT '리뷰 내용',
  image_path VARCHAR(255) NULL COMMENT '이미지 경로',

  FOREIGN KEY (member_id) REFERENCES Member(member_id) ON DELETE CASCADE,
  FOREIGN KEY (prd_id) REFERENCES Product(prd_id) ON DELETE CASCADE
);
