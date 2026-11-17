package com.smartlibrary.dto;

import com.smartlibrary.entity.Book;

public class SemanticSearchResultDto {

    private Book book;
    private double score;
    private String matchedSnippet;
    private String searchMode; // "SEMANTIC_VECTOR" or "KEYWORD_FALLBACK"

    public SemanticSearchResultDto() {}

    public SemanticSearchResultDto(Book book, double score, String matchedSnippet, String searchMode) {
        this.book = book;
        this.score = score;
        this.matchedSnippet = matchedSnippet;
        this.searchMode = searchMode;
    }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getMatchedSnippet() { return matchedSnippet; }
    public void setMatchedSnippet(String matchedSnippet) { this.matchedSnippet = matchedSnippet; }

    public String getSearchMode() { return searchMode; }
    public void setSearchMode(String searchMode) { this.searchMode = searchMode; }
}
