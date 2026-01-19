package com.example.bloodsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "match_records", indexes = {
        @Index(name = "idx_mr_patient", columnList = "patient_id"),
        @Index(name = "idx_mr_donor", columnList = "donor_id")
})
public class MatchRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联患者
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    // 关联供者
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private Donor donor;

    private LocalDateTime matchDate;

    // 记录当时的匹配分数和等级
    private Double score;
    private String grade;

    // 状态: SELECTED(已选中), TRANSFUSED(已输血)
    private String status;

    @PrePersist
    protected void onCreate() {
        if (matchDate == null) matchDate = LocalDateTime.now();
        if (status == null) status = "SELECTED";
    }
}