package com.example.bloodsystem.controller;

import com.example.bloodsystem.entity.Donor;
import com.example.bloodsystem.entity.MatchRecord;
import com.example.bloodsystem.entity.Patient;
import com.example.bloodsystem.service.DonorService;
import com.example.bloodsystem.service.DonorService.MatchResult;
import com.example.bloodsystem.service.ImportResult;
import com.example.bloodsystem.service.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DonorController {

    @Autowired private DonorService service;
    @Autowired private PatientService patientService;

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @GetMapping("/")
    public String index() {
        return "redirect:/match";
    }

    @GetMapping("/match")
    public String matchPage(Model model) {
        model.addAttribute("nav", "match");
        return "match";
    }

    @GetMapping("/donors")
    public String donorList(Model model,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "15") int size,
                            @RequestParam(required = false) String keyword) {
        Page<Donor> p = service.getDonors(page, size, keyword);
        model.addAttribute("donorPage", p);
        model.addAttribute("donors", p.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("nav", "donors");
        return "donor_list";
    }

    @GetMapping("/patients")
    public String patientList(Model model,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "15") int size,
                              @RequestParam(required = false) String keyword) {
        Page<Patient> p = patientService.getPatients(page, size, keyword);
        model.addAttribute("patientPage", p);
        model.addAttribute("patients", p.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("nav", "patients");
        return "patient_list";
    }

    @GetMapping("/profile/donor/{id}")
    public String donorProfile(@PathVariable String id, Model model) {
        Donor d = service.getDonorById(id);
        if (d == null) return "redirect:/donors";
        List<MatchRecord> history = patientService.getHistoryByDonor(id);

        model.addAttribute("person", d);
        model.addAttribute("type", "donor");
        model.addAttribute("history", history);
        model.addAttribute("nav", "donors");
        return "profile";
    }

    @GetMapping("/profile/patient/{id}")
    public String patientProfile(@PathVariable String id, Model model) {
        Patient p = patientService.getPatientById(id);
        if (p == null) return "redirect:/patients";
        List<MatchRecord> history = patientService.getHistoryByPatient(id);

        model.addAttribute("person", p);
        model.addAttribute("type", "patient");
        model.addAttribute("history", history);
        model.addAttribute("nav", "patients");
        return "profile";
    }

    @GetMapping("/add")
    public String add(Model model) {
        model.addAttribute("donor", new Donor());
        return "add_donor";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable String id, Model model) {
        Donor d = service.getDonorById(id);
        if (d == null) return "redirect:/donors";
        model.addAttribute("donor", d);
        return "add_donor";
    }

    @GetMapping("/import")
    public String imp() { return "import_data"; }

    @PostMapping("/save")
    public String save(@ModelAttribute Donor donor, Model model, RedirectAttributes redirectAttributes) {
        try {
            service.saveDonor(donor);
            redirectAttributes.addFlashAttribute("successMessage", "保存成功");
            return "redirect:/donors";
        } catch (ObjectOptimisticLockingFailureException e) {
            model.addAttribute("errorMessage", "保存失败：该数据已被其他人修改，请刷新页面获取最新版本！");
            return "add_donor";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "保存失败：" + e.getMessage());
            return "add_donor";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            service.deleteDonor(id);
            redirectAttributes.addFlashAttribute("successMessage", "删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/donors";
    }

    // 🔥 新增：删除单个患者
    @PostMapping("/delete/patient/{id}")
    public String deletePatient(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            patientService.deletePatient(id);
            redirectAttributes.addFlashAttribute("successMessage", "患者及其配型记录已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/patients";
    }

    // 清空供者库
    @PostMapping("/reset")
    public String reset(RedirectAttributes redirectAttributes) {
        try {
            service.deleteAllDonors();
            redirectAttributes.addFlashAttribute("successMessage", "供者数据库已清空");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/donors";
    }

    // 🔥 新增：清空患者库
    @PostMapping("/reset/patients")
    public String resetPatients(RedirectAttributes redirectAttributes) {
        try {
            patientService.deleteAllPatients();
            redirectAttributes.addFlashAttribute("successMessage", "患者数据库已清空");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/patients";
    }

    @PostMapping("/import")
    public String impPost(@RequestParam("textData") String t, Model m) {
        ImportResult result = service.importFromText(t);
        StringBuilder msg = new StringBuilder();
        msg.append("成功导入 ").append(result.getSuccessCount()).append(" 条数据。");
        if (result.getFailureCount() > 0) {
            msg.append(" 失败 ").append(result.getFailureCount()).append(" 条。");
            msg.append(" <br/>错误详情（前100条）：<br/>");
            msg.append(String.join("<br/>", result.getErrorMessages()));
        }
        m.addAttribute("message", msg.toString());
        return "import_data";
    }

    // --- API 部分 ---

    @PostMapping("/api/match")
    @ResponseBody
    public List<MatchResult> apiMatch(@RequestParam(required = false) String bloodType,
                                      @RequestParam(required = false) String antibodies,
                                      @RequestParam(required = false, defaultValue = "false") boolean limitResult,
                                      @RequestParam Map<String, String> allParams) {
        return service.matchDonors(bloodType, parseParams(allParams), antibodies, limitResult);
    }

    @PostMapping("/api/confirmMatch")
    @ResponseBody
    public String confirmMatch(@RequestParam(required = false) String currentPatientId,
                               @RequestParam String donorId,
                               @RequestParam String patientName,
                               @RequestParam Double score,
                               @RequestParam String grade,
                               @RequestParam Map<String, String> allParams) {
        try {
            Map<String, String> pData = new HashMap<>();

            if(allParams.get("bloodType") != null) pData.put("bloodType", allParams.get("bloodType"));
            if(allParams.get("antibodies") != null) pData.put("antibodies", allParams.get("antibodies"));

            pData.putAll(parseParams(allParams));

            String savedPatientId = patientService.confirmMatch(currentPatientId, donorId, patientName, pData, score, grade);
            return savedPatientId;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("保存失败: " + e.getMessage());
        }
    }

    private Map<String, String> parseParams(Map<String, String> allParams) {
        Map<String, String> map = new HashMap<>();
        if (allParams != null) {
            allParams.forEach((k, v) -> {
                if (v == null || v.trim().isEmpty()) return;

                if (k.startsWith("hpa")) {
                    String number = k.substring(3);
                    map.put("HPA-" + number, v);
                }

                if (k.equals("hlaA1")) map.put("HLA-A1", v.trim());
                if (k.equals("hlaA2")) map.put("HLA-A2", v.trim());
                if (k.equals("hlaB1")) map.put("HLA-B1", v.trim());
                if (k.equals("hlaB2")) map.put("HLA-B2", v.trim());
            });
        }
        return map;
    }
}