package edu.cit.paires.cleanride.controller;

import edu.cit.paires.cleanride.dto.BookingRequest;
import edu.cit.paires.cleanride.dto.SlotDetailResponse;
import edu.cit.paires.cleanride.dto.UserBookingResponse;
import edu.cit.paires.cleanride.entity.Booking;
import edu.cit.paires.cleanride.entity.User;
import edu.cit.paires.cleanride.repository.BookingRepository;
import edu.cit.paires.cleanride.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest request) {
        User user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return new ResponseEntity<>("User not found", HttpStatus.BAD_REQUEST);
        }

        long currentBookings = bookingRepository.countByBookingDateAndTimeSlotAndStatusNot(
                request.getBookingDate(), request.getTimeSlot(), Booking.BookingStatus.CANCELLED);

        if (currentBookings >= 5) {
            return new ResponseEntity<>("Time slot is fully booked", HttpStatus.CONFLICT);
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setBookingDate(request.getBookingDate());
        booking.setTimeSlot(request.getTimeSlot());
        
        // Find next available bay 1-5
        List<Booking> existing = bookingRepository.findByBookingDate(request.getBookingDate()).stream()
                .filter(b -> b.getTimeSlot().equals(request.getTimeSlot()) && b.getStatus() != Booking.BookingStatus.CANCELLED)
                .collect(Collectors.toList());
        
        List<Integer> occupiedBays = existing.stream().map(Booking::getBayId).collect(Collectors.toList());
        int assignedBay = 1;
        for (int i = 1; i <= 5; i++) {
            if (!occupiedBays.contains(i)) {
                assignedBay = i;
                break;
            }
        }
        
        booking.setBayId(assignedBay);
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.setServiceType(request.getServiceType());
        booking.setVehicleType(request.getVehicleType());
        booking.setTotalPrice(request.getTotalPrice());

        String timeChar = switch(request.getTimeSlot()) {
            case 1 -> "8A";
            case 2 -> "9A";
            case 3 -> "11A";
            case 4 -> "12P";
            case 5 -> "2P";
            case 6 -> "3P";
            case 7 -> "5P";
            case 8 -> "6P";
            case 9 -> "8P";
            default -> "0X";
        };
        String dateStr = String.format("%02d%02d", request.getBookingDate().getMonthValue(), request.getBookingDate().getDayOfMonth());
        String priorityString = "CR-" + user.getId() + "-" + dateStr + "-" + timeChar;
        booking.setPriorityNumber(priorityString);
        
        bookingRepository.save(booking);

        return new ResponseEntity<>(priorityString, HttpStatus.CREATED);
    }

    @GetMapping("/daily")
    public ResponseEntity<List<SlotDetailResponse>> getDailySlots(@RequestParam("date") String date) {
        LocalDate parsedDate = LocalDate.parse(date);
            List<Booking> dailyBookings = bookingRepository.findByBookingDate(parsedDate);

            List<SlotDetailResponse> slots = new ArrayList<>();
            
            for (int i = 1; i <= 9; i++) {
                final int slotIndex = i;
                List<Booking> bookingsInSlot = dailyBookings.stream()
                        .filter(b -> b.getTimeSlot() != null && b.getTimeSlot() == slotIndex && b.getStatus() != Booking.BookingStatus.CANCELLED)
                        .collect(Collectors.toList());

            SlotDetailResponse dto = new SlotDetailResponse();
            dto.setTimeSlot(slotIndex);
            dto.setOccupiedCount((long) bookingsInSlot.size());

            // Populate Admin specifics (user details per bay)
            List<SlotDetailResponse.BayDetail> bayDetails = new ArrayList<>();
            for (int bay = 1; bay <= 5; bay++) {
                final int bayId = bay;
                Booking b = bookingsInSlot.stream().filter(bk -> bk.getBayId() != null && bk.getBayId() == bayId).findFirst().orElse(null);
                
                SlotDetailResponse.BayDetail bd = new SlotDetailResponse.BayDetail();
                bd.setBayId(bay);
                if (b != null) {
                    bd.setBookingId(b.getId());
                    bd.setUsername(b.getUser().getUsername());
                    bd.setPriorityFormat(b.getPriorityNumber() != null ? b.getPriorityNumber() : "#P" + String.format("%02d", b.getId()));
                }
                bayDetails.add(bd);
            }
            dto.setBayDetails(bayDetails);
            slots.add(dto);
        }

        return ResponseEntity.ok(slots);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserBookingResponse>> getUserBookings(@PathVariable("userId") Long userId) {
        List<Booking> bookings = bookingRepository.findByUserIdOrderByBookingDateDesc(userId);
        
        List<UserBookingResponse> responses = bookings.stream().map(b -> {
            UserBookingResponse dto = new UserBookingResponse();
            dto.setId(b.getId());
            dto.setPriorityNumber(b.getPriorityNumber());
            dto.setBookingDate(b.getBookingDate());
            dto.setTimeSlot(b.getTimeSlot());
            dto.setBayId(b.getBayId());
            dto.setStatus(b.getStatus());
            dto.setServiceType(b.getServiceType());
            dto.setVehicleType(b.getVehicleType());
            dto.setTotalPrice(b.getTotalPrice());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable("bookingId") Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return new ResponseEntity<>("Booking not found", HttpStatus.NOT_FOUND);
        }

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            return new ResponseEntity<>("Booking is already cancelled", HttpStatus.BAD_REQUEST);
        }
        if (booking.getStatus() == Booking.BookingStatus.COMPLETED) {
            return new ResponseEntity<>("Completed booking cannot be cancelled", HttpStatus.BAD_REQUEST);
        }

        int slotMap = booking.getTimeSlot();
        int hours = 0;
        int mins = 0;
        switch(slotMap) {
            case 1 -> { hours = 8; mins = 0; }
            case 2 -> { hours = 9; mins = 30; }
            case 3 -> { hours = 11; mins = 0; }
            case 4 -> { hours = 12; mins = 30; }
            case 5 -> { hours = 14; mins = 0; }
            case 6 -> { hours = 15; mins = 30; }
            case 7 -> { hours = 17; mins = 0; }
            case 8 -> { hours = 18; mins = 30; }
            case 9 -> { hours = 20; mins = 0; }
        }
        
        java.time.LocalDateTime scheduledTime = java.time.LocalDateTime.of(booking.getBookingDate(), java.time.LocalTime.of(hours, mins));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        
        if (now.isAfter(scheduledTime.minusHours(1))) {
            return new ResponseEntity<>("Cannot cancel within 1 hour of the scheduled time", HttpStatus.BAD_REQUEST);
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return ResponseEntity.ok("Booking successfully cancelled");
    }
}
