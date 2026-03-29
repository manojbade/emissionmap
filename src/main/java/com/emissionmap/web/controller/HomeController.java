package com.emissionmap.web.controller;

import com.emissionmap.web.form.AddressLookupForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final JdbcTemplate jdbc;

    public HomeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("form", new AddressLookupForm());
        loadStats(model);
        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    private void loadStats(Model model) {
        try {
            Long totalFacilities = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT facility_id) FROM tri_releases WHERE year = 2024", Long.class);
            Double totalReleaseLbs = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(on_site_total), 0) FROM tri_releases WHERE year = 2024", Double.class);
            Long carcinogenFacilities = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT facility_id) FROM tri_releases WHERE year = 2024 AND carcinogen = true", Long.class);

            model.addAttribute("totalFacilities", totalFacilities != null ? totalFacilities : 0L);
            model.addAttribute("carcinogenFacilities", carcinogenFacilities != null ? carcinogenFacilities : 0L);

            double lbs = totalReleaseLbs != null ? totalReleaseLbs : 0;
            String formatted;
            if (lbs >= 1_000_000_000) {
                formatted = String.format("%.1fB lbs", lbs / 1_000_000_000);
            } else if (lbs >= 1_000_000) {
                formatted = String.format("%.0fM lbs", lbs / 1_000_000);
            } else {
                formatted = String.format("%.0f lbs", lbs);
            }
            model.addAttribute("totalReleaseBillionsFormatted", formatted);

        } catch (Exception e) {
            log.warn("Could not load homepage stats: {}", e.getMessage());
            model.addAttribute("totalFacilities", 0L);
            model.addAttribute("carcinogenFacilities", 0L);
            model.addAttribute("totalReleaseBillionsFormatted", "");
        }
    }
}
