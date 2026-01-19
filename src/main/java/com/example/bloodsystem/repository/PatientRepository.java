package com.example.bloodsystem.repository;

import com.example.bloodsystem.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {

    // 根据姓名查找患者，用于自动查重
    Optional<Patient> findByName(String name);

    // 搜索功能
    @Query("SELECT p FROM Patient p WHERE p.name LIKE %?1% OR p.patientId LIKE %?1%")
    Page<Patient> search(String keyword, Pageable pageable);
}