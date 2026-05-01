package edu.cit.paires.cleanride.dto;

import lombok.Data;

@Data
public class ReviewDto {
    private Long id;
    private Integer rating;
    private String feedback;
    private String photoUrl;
    private Long bookingId;
    private Long userId;
    private String customerName;
}
