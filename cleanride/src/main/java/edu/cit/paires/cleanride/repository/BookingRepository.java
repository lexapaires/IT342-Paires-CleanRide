package edu.cit.paires.cleanride.repository;

import edu.cit.paires.cleanride.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBookingDate(LocalDate bookingDate);
    
    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    long countByBookingDateAndTimeSlotAndStatusNot(LocalDate bookingDate, Integer timeSlot, Booking.BookingStatus status);
}
