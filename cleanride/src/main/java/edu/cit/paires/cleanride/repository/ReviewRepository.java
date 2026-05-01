package edu.cit.paires.cleanride.repository;

import edu.cit.paires.cleanride.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByUserId(Long userId);
    Optional<Review> findByBookingId(Long bookingId);
}
