package com.smartlibrary.service;

import com.smartlibrary.dto.SystemSettingsFormDto;
import com.smartlibrary.entity.SystemSetting;
import com.smartlibrary.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;

    public SystemSettingService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getIntSetting(String key, int defaultValue) {
        try {
            return Integer.parseInt(getSetting(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public double getDoubleSetting(String key, double defaultValue) {
        try {
            return Double.parseDouble(getSetting(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBooleanSetting(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getSetting(key, String.valueOf(defaultValue)));
    }

    @Transactional
    public void setSetting(String key, String value, String description) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElse(new SystemSetting(key, value, description));
        setting.setSettingValue(value);
        if (description != null) {
            setting.setDescription(description);
        }
        settingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public SystemSettingsFormDto getSettingsForm() {
        SystemSettingsFormDto form = new SystemSettingsFormDto();
        form.setMaxBorrowLimit(getIntSetting("max_borrow_limit", 3));
        form.setBorrowDurationDays(getIntSetting("borrow_duration_days", 14));
        form.setDailyFineRate(getDoubleSetting("daily_fine_rate", 1.00));
        form.setAllowStudentReservations(getBooleanSetting("allow_student_reservations", true));
        form.setAiAssistantEnabled(getBooleanSetting("ai_assistant_enabled", true));
        form.setSemanticSearchThreshold(getDoubleSetting("semantic_search_threshold", 0.65));
        form.setGeminiApiKey(getSetting("gemini_api_key", ""));
        form.setGeminiModel(getSetting("gemini_model", "gemini-1.5-flash"));
        return form;
    }

    @Transactional
    public void saveSettingsForm(SystemSettingsFormDto form) {
        setSetting("max_borrow_limit", String.valueOf(form.getMaxBorrowLimit()), "Max books allowed per student");
        setSetting("borrow_duration_days", String.valueOf(form.getBorrowDurationDays()), "Loan duration in days");
        setSetting("daily_fine_rate", String.valueOf(form.getDailyFineRate()), "Daily overdue fine amount");
        setSetting("allow_student_reservations", String.valueOf(form.isAllowStudentReservations()), "Allow student reservations");
        setSetting("ai_assistant_enabled", String.valueOf(form.isAiAssistantEnabled()), "Enable AI Assistant");
        setSetting("semantic_search_threshold", String.valueOf(form.getSemanticSearchThreshold()), "Cutoff for semantic similarity");
        if (form.getGeminiApiKey() != null && !form.getGeminiApiKey().isBlank()) {
            setSetting("gemini_api_key", form.getGeminiApiKey().trim(), "Google Gemini API Key");
        }
        if (form.getGeminiModel() != null && !form.getGeminiModel().isBlank()) {
            setSetting("gemini_model", form.getGeminiModel().trim(), "Google Gemini Model Name");
        }
    }
}
