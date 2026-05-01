package edu.cit.paires.cleanride.controller;

import edu.cit.paires.cleanride.dto.ReviewDto;
import edu.cit.paires.cleanride.entity.Booking;
import edu.cit.paires.cleanride.entity.Review;
import edu.cit.paires.cleanride.repository.BookingRepository;
import edu.cit.paires.cleanride.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reviews")
@CrossOrigin(origins = "*") // Allows cross-origin requests from frontend
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadReview(
            @RequestParam("bookingId") Long bookingId,
            @RequestParam("rating") Integer rating,
            @RequestParam("feedback") String feedback,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {

        try {
            Optional<Booking> optionalBooking = bookingRepository.findById(bookingId);
            if (optionalBooking.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Booking not found");
            }
            
            Booking booking = optionalBooking.get();
            if (booking.getStatus() != Booking.BookingStatus.COMPLETED && booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot review an incomplete or cancelled booking");
            }

            Optional<Review> existingReview = reviewRepository.findByBookingId(bookingId);
            if (existingReview.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Booking has already been reviewed");
            }

            Review review = new Review();
            review.setBooking(booking);
            review.setUser(booking.getUser());
            review.setRating(rating);
            review.setFeedback(feedback);

            if (photo != null && !photo.isEmpty()) {
                String uploadDir = "uploads";
                Path uploadPath = Paths.get(uploadDir);

                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String filename = System.currentTimeMillis() + "_" + photo.getOriginalFilename();
                Path filePath = uploadPath.resolve(filename);
                Files.copy(photo.getInputStream(), filePath);

                review.setPhotoUrl("/uploads/" + filename);
            }

            reviewRepository.save(review);
            return ResponseEntity.ok("Review submitted successfully");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload photo");
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewDto>> getUserReviews(@PathVariable("userId") Long userId) {
        List<Review> reviews = reviewRepository.findByUserId(userId);
        List<ReviewDto> dtos = reviews.stream().map(r -> {
            ReviewDto dto = new ReviewDto();
            dto.setId(r.getId());
            dto.setRating(r.getRating());
            dto.setFeedback(r.getFeedback());
            dto.setPhotoUrl(r.getPhotoUrl());
            dto.setBookingId(r.getBooking().getId());
            dto.setUserId(r.getUser().getId());
            dto.setCustomerName(r.getUser().getFirstName() + " " + r.getUser().getLastName());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/all")
    public ResponseEntity<List<ReviewDto>> getAllReviews() {
        List<Review> reviews = reviewRepository.findAll();
        List<ReviewDto> dtos = reviews.stream()
                .sorted((r1, r2) -> r2.getId().compareTo(r1.getId())) // Newest first
                .map(r -> {
                    ReviewDto dto = new ReviewDto();
                    dto.setId(r.getId());
                    dto.setRating(r.getRating());
                    dto.setFeedback(r.getFeedback());
                    dto.setPhotoUrl(r.getPhotoUrl());
                    dto.setBookingId(r.getBooking().getId());
                    dto.setUserId(r.getUser().getId());
                    dto.setCustomerName(r.getUser().getFirstName() + " " + r.getUser().getLastName());
                    return dto;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
