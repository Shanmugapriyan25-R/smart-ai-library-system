package com.smartlibrary.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "library_seats")
public class LibrarySeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Seat number is required")
    @Column(name = "seat_number", nullable = false, unique = true, length = 20)
    private String seatNumber;

    @Column(nullable = false, length = 50)
    private String floor = "1st Floor";

    @Column(nullable = false, length = 50)
    private String section = "Quiet Study";

    @Column(name = "has_power_outlet", nullable = false)
    private Boolean hasPowerOutlet = true;

    @Column(name = "is_accessible", nullable = false)
    private Boolean isAccessible = true;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public LibrarySeat() {}

    public LibrarySeat(String seatNumber, String floor, String section, Boolean hasPowerOutlet, Boolean isAccessible) {
        this.seatNumber = seatNumber;
        this.floor = floor;
        this.section = section;
        this.hasPowerOutlet = hasPowerOutlet;
        this.isAccessible = isAccessible;
        this.status = "ACTIVE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Boolean getHasPowerOutlet() { return hasPowerOutlet; }
    public void setHasPowerOutlet(Boolean hasPowerOutlet) { this.hasPowerOutlet = hasPowerOutlet; }

    public Boolean getIsAccessible() { return isAccessible; }
    public void setIsAccessible(Boolean isAccessible) { this.isAccessible = isAccessible; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
