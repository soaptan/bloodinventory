package com.fyp.bloodinventory.dto;

public class SmartSearchResultDto {

    private String title;
    private String description;
    private String url;
    private String category;
    private double score;
    private String matchType;

    public SmartSearchResultDto() {
    }

    public SmartSearchResultDto(String title,
                                String description,
                                String url,
                                String category,
                                double score,
                                String matchType) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.category = category;
        this.score = score;
        this.matchType = matchType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }
}
