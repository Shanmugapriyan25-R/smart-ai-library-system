package com.smartlibrary.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatRequestDto {

    @NotBlank(message = "Query cannot be blank")
    private String query;

    private boolean enableRag = true;

    public AiChatRequestDto() {}

    public AiChatRequestDto(String query) {
        this.query = query;
        this.enableRag = true;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public boolean isEnableRag() { return enableRag; }
    public void setEnableRag(boolean enableRag) { this.enableRag = enableRag; }
}
