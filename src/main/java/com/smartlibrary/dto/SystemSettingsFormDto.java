package com.smartlibrary.dto;

public class SystemSettingsFormDto {

    private int maxBorrowLimit = 3;
    private int borrowDurationDays = 14;
    private double dailyFineRate = 1.00;
    private boolean allowStudentReservations = true;
    private boolean aiAssistantEnabled = true;
    private double semanticSearchThreshold = 0.65;
    private String geminiApiKey = "";
    private String geminiModel = "gemini-1.5-flash";

    public SystemSettingsFormDto() {}

    public int getMaxBorrowLimit() { return maxBorrowLimit; }
    public void setMaxBorrowLimit(int maxBorrowLimit) { this.maxBorrowLimit = maxBorrowLimit; }

    public int getBorrowDurationDays() { return borrowDurationDays; }
    public void setBorrowDurationDays(int borrowDurationDays) { this.borrowDurationDays = borrowDurationDays; }

    public double getDailyFineRate() { return dailyFineRate; }
    public void setDailyFineRate(double dailyFineRate) { this.dailyFineRate = dailyFineRate; }

    public boolean isAllowStudentReservations() { return allowStudentReservations; }
    public void setAllowStudentReservations(boolean allowStudentReservations) { this.allowStudentReservations = allowStudentReservations; }

    public boolean isAiAssistantEnabled() { return aiAssistantEnabled; }
    public void setAiAssistantEnabled(boolean aiAssistantEnabled) { this.aiAssistantEnabled = aiAssistantEnabled; }

    public double getSemanticSearchThreshold() { return semanticSearchThreshold; }
    public void setSemanticSearchThreshold(double semanticSearchThreshold) { this.semanticSearchThreshold = semanticSearchThreshold; }

    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }

    public String getGeminiModel() { return geminiModel; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
}
