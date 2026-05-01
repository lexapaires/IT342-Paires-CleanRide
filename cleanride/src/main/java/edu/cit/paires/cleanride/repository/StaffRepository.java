package edu.cit.paires.cleanride.repository;

import edu.cit.paires.cleanride.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByStatus(Staff.StaffStatus status);
}
