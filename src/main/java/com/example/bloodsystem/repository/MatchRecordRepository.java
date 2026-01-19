package com.example.bloodsystem.repository;

import com.example.bloodsystem.entity.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    // 查询某供者的所有捐献历史，按时间倒序
    List<MatchRecord> findByDonorDonorIdOrderByMatchDateDesc(String donorId);

    // 查询某患者的所有受血历史，按时间倒序
    List<MatchRecord> findByPatientPatientIdOrderByMatchDateDesc(String patientId);
}