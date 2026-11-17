package com.smartlibrary.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reading_progress",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "digital_resource_id"})})
public class ReadingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "digital_resource_id", nullable = false)
    private DigitalResource digitalResource;

    @Column(name = "last_page", nullable = false)
    private Integer lastPage = 1;

    @Column(name = "total_pages", nullable = false)
    private Integer totalPages = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt = LocalDateTime.now();

    public ReadingProgress() {}

    public ReadingProgress(User user, DigitalResource digitalResource, Integer lastPage, Integer totalPages) {
        this.user = user;
        this.digitalResource = digitalResource;
        this.lastPage = lastPage;
        this.totalPages = totalPages;
        this.lastReadAt = LocalDateTime.now();
    }

    public int getProgressPercent() {
        if (totalPages == null || totalPages <= 0) return 0;
        int pct = (int) Math.round((double) lastPage / totalPages * 100);
        return Math.min(100, Math.max(0, pct));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public DigitalResource getDigitalResource() { return digitalResource; }
    public void setDigitalResource(DigitalResource digitalResource) { this.digitalResource = digitalResource; }

    public Integer getLastPage() { return lastPage; }
    public void setLastPage(Integer lastPage) { this.lastPage = lastPage; }

    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
}
