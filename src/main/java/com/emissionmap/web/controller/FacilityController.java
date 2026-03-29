package com.emissionmap.web.controller;

import com.emissionmap.domain.view.FacilityViewModel;
import com.emissionmap.domain.view.FacilityYearSummary;
import com.emissionmap.service.EmissionLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
public class FacilityController {

    private final EmissionLookupService lookupService;
    private final ObjectMapper objectMapper;

    public FacilityController(EmissionLookupService lookupService, ObjectMapper objectMapper) {
        this.lookupService = lookupService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/facility/{id}")
    public String facility(@PathVariable("id") String facilityId,
                           Model model,
                           HttpServletResponse response) {
        FacilityViewModel vm;

        try {
            vm = lookupService.loadFacilityDetails(facilityId)
                    .orElseGet(() -> FacilityViewModel.error(
                            facilityId,
                            "We couldn't find a TRI facility with that ID."
                    ));
        } catch (DataAccessException ex) {
            vm = FacilityViewModel.error(
                    facilityId,
                    "Facility detail data isn't available in this profile. Run against devpg or production data to view this page."
            );
        }

        if (vm.hasError()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        model.addAttribute("vm", vm);
        model.addAttribute("yearlyTotalsJson", buildYearlyTotalsJson(vm));
        model.addAttribute("topChemicalsJson", buildTopChemicalsJson(vm));
        return "facility";
    }

    private String buildYearlyTotalsJson(FacilityViewModel vm) {
        if (vm.hasError()) {
            return "[]";
        }

        List<Map<String, Object>> payload = vm.yearlyReleases().stream()
                .sorted(Comparator.comparingInt(FacilityYearSummary::year))
                .map(summary -> Map.<String, Object>of(
                        "year", String.valueOf(summary.year()),
                        "total", summary.totalReleaseLbs()
                ))
                .toList();

        return writeJson(payload);
    }

    private String buildTopChemicalsJson(FacilityViewModel vm) {
        if (vm.hasError()) {
            return "[]";
        }

        List<Map<String, Object>> payload = vm.topChemicals().stream()
                .map(chemical -> Map.<String, Object>of(
                        "chemical", chemical.chemical(),
                        "total", chemical.totalReleaseLbs()
                ))
                .toList();

        return writeJson(payload);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
