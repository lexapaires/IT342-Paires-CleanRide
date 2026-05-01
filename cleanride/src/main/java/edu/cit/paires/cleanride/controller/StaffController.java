package edu.cit.paires.cleanride.controller;

import edu.cit.paires.cleanride.entity.Staff;
import edu.cit.paires.cleanride.repository.StaffRepository;
import edu.cit.paires.cleanride.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/staff")
@CrossOrigin(origins = "*")
public class StaffController {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping
    public ResponseEntity<List<Staff>> getAllStaff() {
        return ResponseEntity.ok(staffRepository.findAll());
    }

    @GetMapping("/on-duty")
    public ResponseEntity<List<Staff>> getOnDutyStaff() {
        return ResponseEntity.ok(staffRepository.findByStatus(Staff.StaffStatus.ON_DUTY));
    }

    @PostMapping
    public ResponseEntity<Staff> createStaff(@RequestBody Staff req) {
        if (req.getStatus() == null) {
            req.setStatus(Staff.StaffStatus.OFF_DUTY);
        }
        Staff saved = staffRepository.save(req);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String statusStr) {
        Optional<Staff> opt = staffRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Staff staff = opt.get();
        try {
            staff.setStatus(Staff.StaffStatus.valueOf(statusStr.toUpperCase()));
            staffRepository.save(staff);
            return ResponseEntity.ok(staff);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        if (bookingRepository.existsByAssignedStaffId(id)) {
            return ResponseEntity.badRequest().body("unable to delete. staff is assigned to existing booking");
        }

        try {
            staffRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Unexpected error connecting to database.");
        }
    }
}
