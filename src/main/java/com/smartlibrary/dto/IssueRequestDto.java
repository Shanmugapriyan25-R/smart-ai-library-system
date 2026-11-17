package com.smartlibrary.dto;

import jakarta.validation.constraints.NotNull;

public class IssueRequestDto {

    @NotNull(message = "Book ID is required")
    private Long bookId;

    private String remarks;

    public IssueRequestDto() {}

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
