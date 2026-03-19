package edu.cit.paires.cleanride.dto;

import edu.cit.paires.cleanride.entity.Booking.BookingStatus;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UserBookingResponse {
    private Long id;
    private String priorityNumber;
    private LocalDate bookingDate;
    private Integer timeSlot;
    private Integer bayId;
    private BookingStatus status;
    private String serviceType;
    private String vehicleType;
    private Double totalPrice;
}
