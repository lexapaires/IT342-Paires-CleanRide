package edu.cit.paires.cleanride.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BookingRequest {
    private Long userId; // For now trusting the frontend request, eventually extract from JWT
    private LocalDate bookingDate;
    private Integer timeSlot;
    private String serviceType;
    private String vehicleType;
    private Double totalPrice;
}
