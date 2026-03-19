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
        booking.setStatus(Booking.BookingStatus.PENDING);
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
    public ResponseEntity<List<SlotDetailResponse>> getDailySlots(@RequestParam String date) {
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
                    bd.setPriorityFormat("#P" + String.format("%02d", b.getId()));
                }
                bayDetails.add(bd);
            }
            dto.setBayDetails(bayDetails);
            slots.add(dto);
        }

        return ResponseEntity.ok(slots);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserBookingResponse>> getUserBookings(@PathVariable Long userId) {
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
}
