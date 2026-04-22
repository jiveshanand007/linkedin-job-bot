package com.jobbot.repository;

import com.jobbot.entity.Application;
import com.jobbot.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByJob(Job job);
    List<Application> findByStatus(String status);
}
