package edu.cit.paires.cleanride.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "username")
    private String username;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "time_slot", nullable = false)
    private Integer timeSlot; // 1 to 9 mapping to the time blocks

    @Column(name = "bay_id")
    private Integer bayId; // 1 to 5, assigned by admin or sequentially

    @Column(name = "service_type")
    private String serviceType;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "total_price")
    private Double totalPrice;

    @Column(name = "priority_number")
    private String priorityNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "current_stage_index")
    private Integer currentStageIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum BookingStatus {
        PENDING, ACTIVE, CONFIRMED, COMPLETED, CANCELLED
    }
}
