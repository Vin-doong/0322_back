// ProductResponse.java
package com.suppleit.backend.dto;

public class ProductResponse {
  private String title;
  private String link;
  private String image;
  private int price;
  private boolean isDummy; // 추가된 필드

  // 기존 생성자
  public ProductResponse(String title, String link, String image, int price) {
    this.title = title;
    this.link = link;
    this.image = image;
    this.price = price;
    this.isDummy = false; // 기본값 false
  }

  // 더미 표시 포함 생성자
  public ProductResponse(String title, String link, String image, int price, boolean isDummy) {
    this.title = title;
    this.link = link;
    this.image = image;
    this.price = price;
    this.isDummy = isDummy;
  }

  // Getter와 Setter 메소드들...
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public int getPrice() {
    return price;
  }

  public void setPrice(int price) {
    this.price = price;
  }

  public boolean isDummy() {
    return isDummy;
  }

  public void setDummy(boolean dummy) {
    isDummy = dummy;
  }
}