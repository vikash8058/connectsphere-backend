package com.connectsphere.notification.repository;

import com.connectsphere.notification.entity.Report;
import com.connectsphere.notification.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Integer> {
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    long countByStatus(ReportStatus status);
}
