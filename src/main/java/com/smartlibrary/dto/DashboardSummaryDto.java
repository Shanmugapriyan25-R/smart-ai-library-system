package com.smartlibrary.dto;

public class DashboardSummaryDto {

    private long totalBooks;
    private long totalCopies;
    private long availableCopies;
    private long activeBorrowings;
    private long pendingRequests;
    private long overdueCount;
    private long totalStudents;
    private long totalStaff;
    private long activeReservations;
    private long studyMinutesLogged;

    public DashboardSummaryDto() {}

    public long getTotalBooks() { return totalBooks; }
    public void setTotalBooks(long totalBooks) { this.totalBooks = totalBooks; }

    public long getTotalCopies() { return totalCopies; }
    public void setTotalCopies(long totalCopies) { this.totalCopies = totalCopies; }

    public long getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(long availableCopies) { this.availableCopies = availableCopies; }

    public long getActiveBorrowings() { return activeBorrowings; }
    public void setActiveBorrowings(long activeBorrowings) { this.activeBorrowings = activeBorrowings; }

    public long getPendingRequests() { return pendingRequests; }
    public void setPendingRequests(long pendingRequests) { this.pendingRequests = pendingRequests; }

    public long getOverdueCount() { return overdueCount; }
    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getTotalStaff() { return totalStaff; }
    public void setTotalStaff(long totalStaff) { this.totalStaff = totalStaff; }

    public long getActiveReservations() { return activeReservations; }
    public void setActiveReservations(long activeReservations) { this.activeReservations = activeReservations; }

    public long getStudyMinutesLogged() { return studyMinutesLogged; }
    public void setStudyMinutesLogged(long studyMinutesLogged) { this.studyMinutesLogged = studyMinutesLogged; }
}
