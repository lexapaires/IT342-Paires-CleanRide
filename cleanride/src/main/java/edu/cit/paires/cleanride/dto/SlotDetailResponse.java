package edu.cit.paires.cleanride.dto;

import lombok.Data;
import java.util.List;

@Data
public class SlotDetailResponse {
    private Integer timeSlot;
    private Long occupiedCount;
    private List<BayDetail> bayDetails; // Specifically for ADMIN view

    @Data
    public static class BayDetail {
        private Integer bayId;
        private Long bookingId;
        private String username;
        private String priorityFormat;
        private Integer currentStageIndex;
        private String serviceType;
        private String assignedStaffName;
    }
}
