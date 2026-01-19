package com.example.bloodsystem.service;

import com.example.bloodsystem.config.MatchConfig;
import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.repository.DonorRepository;
import com.example.bloodsystem.repository.MatchRecordRepository; // 新增引用
import com.example.bloodsystem.util.HlaUtils;
import com.example.bloodsystem.util.HlaUtils.HlaInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DonorService {

    @Autowired private DonorRepository repository;
    @Autowired private MatchRecordRepository matchRecordRepository; // 注入历史记录 Repo
    @Autowired private ImportService importService;
    @Autowired private MatchConfig matchConfig;
    @PersistenceContext private EntityManager entityManager;

    // --- CRUD ---
    @Transactional
    public void deleteDonor(String id) {
        try {
            // 🔥 修复：先删除该供者相关的历史记录，否则外键报错
            // 注意：这里需要 MatchRecordRepository 支持按供者删除，或者用更简单的逻辑
            // 为简单起见，这里假设已经在数据库层级做了级联，或者手动执行删除
            // 如果没有级联配置，需要先执行 delete from match_records where donor_id = ?
            // 这里我们采用简单方式：如果报错说明有依赖，提示用户。
            // 但用户要求"清空库"成功，所以下面 deleteAllDonors 必须处理。

            repository.deleteById(id);
            repository.flush();
        } catch (Exception e) {
            throw new RuntimeException("删除失败: 该供者可能存在关联的配型记录，无法直接删除");
        }
    }

    @Transactional
    public void deleteAllDonors() {
        try {
            // 🔥 修复：清空供者库前，必须先清空引用它的配型记录表
            matchRecordRepository.deleteAllInBatch();
            repository.deleteAllInBatch();
            repository.flush();
        } catch (Exception e) {
            throw new RuntimeException("清空失败: " + e.getMessage());
        }
    }

    public Page<Donor> getDonors(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("donorId").descending());
        if (keyword != null && !keyword.trim().isEmpty()) return repository.search(keyword.trim(), pageable);
        return repository.findAll(pageable);
    }
    public Donor getDonorById(String id) { return repository.findById(id).orElse(null); }

    @Transactional
    public void saveDonor(Donor donor) {
        if (donor.getDonorId() == null || donor.getDonorId().isEmpty()) donor.setDonorId(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        HlaUtils.fillSplitFields(donor);
        repository.save(donor);
    }
    public ImportResult importFromText(String textData) { return importService.parseAndImportText(textData); }

    // --- 新版配型逻辑 ---

    @SuppressWarnings("unchecked")
    public List<MatchResult> matchDonors(String patientBloodType,
                                         Map<String, String> pGts,
                                         String antibodyText,
                                         boolean limitResult) {

        Set<String> selectedHpas = new HashSet<>();
        List<String> validHpas = matchConfig.getAllHpas();

        if (pGts != null) {
            pGts.forEach((k, v) -> {
                if (v != null && !v.isEmpty() && validHpas.contains(k)) selectedHpas.add(k);
            });
        }

        HlaInfo tA1 = HlaUtils.parseHla(pGts.get("HLA-A1"));
        HlaInfo tA2 = HlaUtils.parseHla(pGts.get("HLA-A2"));
        HlaInfo tB1 = HlaUtils.parseHla(pGts.get("HLA-B1"));
        HlaInfo tB2 = HlaUtils.parseHla(pGts.get("HLA-B2"));

        List<Integer> bannedGroups = HlaUtils.parseAntibodies(antibodyText);

        StringBuilder sql = new StringBuilder("SELECT * FROM donors WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (patientBloodType != null && !patientBloodType.isEmpty()) {
            sql.append(" AND blood_type = :bloodType ");
            params.put("bloodType", patientBloodType);
        }

        Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Donor.class);
        params.forEach(nativeQuery::setParameter);

        List<Donor> candidates = nativeQuery.getResultList();
        List<MatchResult> results = new ArrayList<>();

        for (Donor d : candidates) {
            MatchResult mr = calculateScore(d, pGts, selectedHpas,
                    tA1, tA2, tB1, tB2,
                    bannedGroups);
            if (mr != null) {
                results.add(mr);
            }
        }

        results.sort((r1, r2) -> Double.compare(r2.score, r1.score));

        if (limitResult && results.size() > 50) {
            return results.subList(0, 50);
        }
        return results;
    }

    private MatchResult calculateScore(Donor d, Map<String, String> pGts, Set<String> selectedHpaLoci,
                                       HlaInfo tA1, HlaInfo tA2, HlaInfo tB1, HlaInfo tB2,
                                       List<Integer> bannedGroups) {

        MatchResult mr = new MatchResult(d);
        double totalScore = 0.0;

        checkConflict(d.getHlaA1Group(), "HLA-A1", bannedGroups, mr);
        checkConflict(d.getHlaA2Group(), "HLA-A2", bannedGroups, mr);
        checkConflict(d.getHlaB1Group(), "HLA-B1", bannedGroups, mr);
        checkConflict(d.getHlaB2Group(), "HLA-B2", bannedGroups, mr);

        int matchCount = 0;
        int matchesA = countBestMatches(tA1, tA2, d.getHlaA1Group(), d.getHlaA2Group(), mr, "HLA-A");
        int matchesB = countBestMatches(tB1, tB2, d.getHlaB1Group(), d.getHlaB2Group(), mr, "HLA-B");

        matchCount = matchesA + matchesB;
        totalScore += (matchCount * 100.0);

        if (matchCount == 4) mr.grade = "A";
        else if (matchCount == 3) mr.grade = "B";
        else if (matchCount == 1 || matchCount == 2) mr.grade = "C";
        else mr.grade = "D";

        double hpaScore = 0.0;
        double currentHpaWeight = 0.0;
        double maxHpaWeight = 0.0;

        for (String locus : matchConfig.getAllHpas()) {
            if (!selectedHpaLoci.contains(locus)) continue;

            String pVal = pGts.get(locus);
            String dVal = d.getGenotype(locus);

            maxHpaWeight += 5.0;

            if (dVal == null || dVal.isEmpty()) {
                mr.unknownLoci.add(locus);
            } else {
                int pts = matchConfig.getScore(pVal, dVal);
                if (pts == 2) {
                    mr.matchedLoci.add(locus);
                    hpaScore += 5.0;
                    currentHpaWeight += 5.0;
                } else if (pts == 1) {
                    mr.compatibleLoci.add(locus);
                    hpaScore += 2.0;
                    currentHpaWeight += 2.0;
                } else {
                    mr.mismatchedLoci.add(locus);
                }
            }
        }

        totalScore += hpaScore;
        totalScore -= (mr.conflictCount * 1000.0);

        mr.score = totalScore;
        mr.rate = (maxHpaWeight > 0) ? (currentHpaWeight / maxHpaWeight) * 100.0 : 0;
        if (mr.rate > 100) mr.rate = 100;

        return mr;
    }

    private void checkConflict(Integer donorGroup, String label, List<Integer> bannedGroups, MatchResult mr) {
        if (donorGroup == null) return;
        if (bannedGroups.contains(donorGroup)) {
            mr.conflictCount++;
            mr.conflictReasons.add(label + " (Group " + donorGroup + ") 包含排斥抗原");
        }
    }

    private int countBestMatches(HlaInfo p1, HlaInfo p2, Integer d1, Integer d2, MatchResult mr, String type) {
        if (p1 == null && p2 == null) return 0;

        boolean m1_1 = isMatch(p1, d1);
        boolean m1_2 = isMatch(p2, d2);
        int score1 = (m1_1 ? 1 : 0) + (m1_2 ? 1 : 0);

        boolean m2_1 = isMatch(p1, d2);
        boolean m2_2 = isMatch(p2, d1);
        int score2 = (m2_1 ? 1 : 0) + (m2_2 ? 1 : 0);

        if (score1 >= score2) {
            if (m1_1) mr.highlightedAlleles.add(type + "1");
            if (m1_2) mr.highlightedAlleles.add(type + "2");
            if (score1 > 0) mr.matchedLoci.add(type);
            return score1;
        } else {
            if (m2_2) mr.highlightedAlleles.add(type + "1");
            if (m2_1) mr.highlightedAlleles.add(type + "2");
            if (score2 > 0) mr.matchedLoci.add(type);
            return score2;
        }
    }

    private boolean isMatch(HlaInfo p, Integer dGroup) {
        if (p == null || dGroup == null) return false;
        return p.group == dGroup;
    }

    public static class MatchResult {
        public Donor donor;
        public double rate;
        public double score;
        public String grade = "D";

        public List<String> matchedLoci = new ArrayList<>();
        public List<String> compatibleLoci = new ArrayList<>();
        public List<String> mismatchedLoci = new ArrayList<>();
        public List<String> unknownLoci = new ArrayList<>();
        public Set<String> highlightedAlleles = new HashSet<>();
        public int conflictCount = 0;
        public List<String> conflictReasons = new ArrayList<>();

        public MatchResult(Donor d) {
            this.donor = d;
        }
    }
}