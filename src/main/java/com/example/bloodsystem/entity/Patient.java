package com.example.bloodsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.LinkedHashMap; // 🔥 修改
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_p_name", columnList = "name"),
        @Index(name = "idx_p_blood", columnList = "blood_type")
})
public class Patient {
    @Id
    @Column(length = 50)
    private String patientId;

    @Column(nullable = false)
    private String name;

    private String gender;
    private Integer age;

    @Column(length = 10, name = "blood_type")
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String antibodies;

    // --- HPA ---
    @Column(length = 10) private String hpa1;
    @Column(length = 10) private String hpa2;
    @Column(length = 10) private String hpa3;
    @Column(length = 10) private String hpa4;
    @Column(length = 10) private String hpa5;
    @Column(length = 10) private String hpa6;
    @Column(length = 10) private String hpa10;
    @Column(length = 10) private String hpa15;
    @Column(length = 10) private String hpa21;

    // --- HLA ---
    @Column(length = 20) private String hlaA1;
    @Column(length = 20) private String hlaA2;
    @Column(length = 20) private String hlaB1;
    @Column(length = 20) private String hlaB2;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (patientId == null) patientId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 🔥 修复：使用 LinkedHashMap 保证顺序
    public Map<String, String> getGenotypesMap() {
        Map<String, String> map = new LinkedHashMap<>();
        if (hpa1 != null) map.put("HPA-1", hpa1);
        if (hpa2 != null) map.put("HPA-2", hpa2);
        if (hpa3 != null) map.put("HPA-3", hpa3);
        if (hpa4 != null) map.put("HPA-4", hpa4);
        if (hpa5 != null) map.put("HPA-5", hpa5);
        if (hpa6 != null) map.put("HPA-6", hpa6);
        if (hpa10 != null) map.put("HPA-10", hpa10);
        if (hpa15 != null) map.put("HPA-15", hpa15);
        if (hpa21 != null) map.put("HPA-21", hpa21);
        return map;
    }
}