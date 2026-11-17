package com.smartlibrary.dto;

import jakarta.validation.constraints.NotBlank;

public class DigitalResourceDto {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String author;
    private Long categoryId;
    private String description;
    private String accessLevel = "STUDENT";

    public DigitalResourceDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
}
