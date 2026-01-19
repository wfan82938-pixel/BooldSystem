package com.example.bloodsystem.service;

import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.entity.MatchRecord;
import com.example.bloodsystem.entity.Patient;
import com.example.bloodsystem.repository.DonorRepository;
import com.example.bloodsystem.repository.MatchRecordRepository;
import com.example.bloodsystem.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PatientService {

    @Autowired private PatientRepository patientRepository;
    @Autowired private MatchRecordRepository matchRecordRepository;
    @Autowired private DonorRepository donorRepository;

    public Page<Patient> getPatients(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (keyword != null && !keyword.trim().isEmpty()) {
            return patientRepository.search(keyword.trim(), pageable);
        }
        return patientRepository.findAll(pageable);
    }

    public Patient getPatientById(String id) {
        return patientRepository.findById(id).orElse(null);
    }

    public List<MatchRecord> getHistoryByPatient(String patientId) {
        return matchRecordRepository.findByPatientPatientIdOrderByMatchDateDesc(patientId);
    }

    public List<MatchRecord> getHistoryByDonor(String donorId) {
        return matchRecordRepository.findByDonorDonorIdOrderByMatchDateDesc(donorId);
    }

    @Transactional
    public void deletePatient(String patientId) {
        try {
            List<MatchRecord> records = matchRecordRepository.findByPatientPatientIdOrderByMatchDateDesc(patientId);
            matchRecordRepository.deleteAll(records);
            patientRepository.deleteById(patientId);
        } catch (Exception e) {
            throw new RuntimeException("删除患者失败: " + e.getMessage());
        }
    }

    // 🔥 新增：清空所有患者
    @Transactional
    public void deleteAllPatients() {
        try {
            // 先删除所有配型记录（因为配型记录依赖患者）
            matchRecordRepository.deleteAllInBatch();
            patientRepository.deleteAllInBatch();
        } catch (Exception e) {
            throw new RuntimeException("清空失败: " + e.getMessage());
        }
    }

    @Transactional
    public String confirmMatch(String inputPatientId, String donorId, String patientName, Map<String, String> patientData,
                               Double score, String grade) {

        Patient p;
        if (inputPatientId != null && !inputPatientId.isEmpty()) {
            p = patientRepository.findById(inputPatientId).orElse(new Patient());
        } else {
            p = new Patient();
        }

        p.setName(patientName);
        updatePatientData(p, patientData);
        p = patientRepository.save(p);

        Donor d = donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("供者ID不存在: " + donorId));

        MatchRecord record = new MatchRecord();
        record.setPatient(p);
        record.setDonor(d);
        record.setScore(score);
        record.setGrade(grade);
        matchRecordRepository.save(record);

        return p.getPatientId();
    }

    private void updatePatientData(Patient p, Map<String, String> data) {
        if (data == null) return;
        if (data.containsKey("bloodType")) p.setBloodType(data.get("bloodType"));
        if (data.containsKey("antibodies")) p.setAntibodies(data.get("antibodies"));

        if (data.containsKey("HLA-A1")) p.setHlaA1(data.get("HLA-A1"));
        if (data.containsKey("HLA-A2")) p.setHlaA2(data.get("HLA-A2"));
        if (data.containsKey("HLA-B1")) p.setHlaB1(data.get("HLA-B1"));
        if (data.containsKey("HLA-B2")) p.setHlaB2(data.get("HLA-B2"));

        if (data.containsKey("HPA-1")) p.setHpa1(data.get("HPA-1"));
        if (data.containsKey("HPA-2")) p.setHpa2(data.get("HPA-2"));
        if (data.containsKey("HPA-3")) p.setHpa3(data.get("HPA-3"));
        if (data.containsKey("HPA-4")) p.setHpa4(data.get("HPA-4"));
        if (data.containsKey("HPA-5")) p.setHpa5(data.get("HPA-5"));
        if (data.containsKey("HPA-6")) p.setHpa6(data.get("HPA-6"));
        if (data.containsKey("HPA-10")) p.setHpa10(data.get("HPA-10"));
        if (data.containsKey("HPA-15")) p.setHpa15(data.get("HPA-15"));
        if (data.containsKey("HPA-21")) p.setHpa21(data.get("HPA-21"));
    }
}