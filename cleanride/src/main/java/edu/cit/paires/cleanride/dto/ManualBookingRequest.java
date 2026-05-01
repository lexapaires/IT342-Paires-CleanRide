package edu.cit.paires.cleanride.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ManualBookingRequest {
    private String email;
    private String username;
    private LocalDate bookingDate;
    private Integer timeSlot;
    private String serviceType;
    private String vehicleType;
    private Double totalPrice;
}
