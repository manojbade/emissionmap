package com.emissionmap.web.controller;

import com.emissionmap.config.AppProperties;
import com.emissionmap.domain.model.FacilityResult;
import com.emissionmap.domain.model.GeocodedAddress;
import com.emissionmap.domain.view.ResultsViewModel;
import com.emissionmap.service.EmissionLookupService;
import com.emissionmap.service.GeocodingService;
import com.emissionmap.web.form.AddressLookupForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ResultsController {

    private final GeocodingService geocodingService;
    private final EmissionLookupService lookupService;
    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public ResultsController(GeocodingService geocodingService,
                             EmissionLookupService lookupService,
                             JdbcTemplate jdbc,
                             AppProperties props,
                             ObjectMapper objectMapper) {
        this.geocodingService = geocodingService;
        this.lookupService = lookupService;
        this.jdbc = jdbc;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/lookup")
    public String lookup(@Valid @ModelAttribute("form") AddressLookupForm form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "home";
        }

        int radius = clamp(form.getRadius(), 1, props.getLookup().getMaxRadiusMiles());
        int year = form.getYear();

        try {
            GeocodedAddress geo = geocodingService.geocode(form.getAddress());
            List<FacilityResult> facilities = lookupService.lookupByCoords(geo, radius, year);

            logLookup(geo, radius, facilities);

            ResultsViewModel vm = new ResultsViewModel(geo, facilities, radius, year, null);
            model.addAttribute("vm", vm);
            model.addAttribute("facilitiesJson", buildFacilitiesJson(facilities));
            model.addAttribute("mapCenterJson", buildMapCenterJson(geo));
            return "results";

        } catch (IllegalArgumentException e) {
            ResultsViewModel vm = ResultsViewModel.error(form.getAddress(),
                    "We couldn't locate that address. Please try a more specific address (include city and state).");
            model.addAttribute("vm", vm);
            model.addAttribute("facilitiesJson", "[]");
            model.addAttribute("mapCenterJson", "{}");
            model.addAttribute("form", form);
            return "results";

        } catch (Exception e) {
            ResultsViewModel vm = ResultsViewModel.error(form.getAddress(),
                    "Something went wrong. Please try again.");
            model.addAttribute("vm", vm);
            model.addAttribute("facilitiesJson", "[]");
            model.addAttribute("mapCenterJson", "{}");
            model.addAttribute("form", form);
            return "results";
        }
    }

    private String buildFacilitiesJson(List<FacilityResult> facilities) {
        List<Map<String, Object>> markers = new ArrayList<>();
        for (FacilityResult f : facilities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.facilityId());
            m.put("name", f.name());
            m.put("lat", f.lat());
            m.put("lng", f.lng());
            m.put("totalLbs", f.totalReleaseLbs());
            m.put("carcinogen", f.hasCarcinogen());
            m.put("pfas", f.hasPfas());
            m.put("pbt", f.hasPbt());
            markers.add(m);
        }
        try {
            return objectMapper.writeValueAsString(markers);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildMapCenterJson(GeocodedAddress geo) {
        try {
            return objectMapper.writeValueAsString(Map.of("lat", geo.lat(), "lng", geo.lng()));
        } catch (Exception e) {
            return "{}";
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void logLookup(GeocodedAddress geo, int radius, List<FacilityResult> facilities) {
        try {
            int nearest = facilities.isEmpty() ? -1
                    : (int) (facilities.stream()
                        .mapToDouble(FacilityResult::distanceMiles)
                        .min().orElse(-1) * 5280);
            jdbc.update("""
                    INSERT INTO lookup_audit
                        (searched_at, address_state, radius_miles, facilities_found, nearest_dist_ft, geocoder_used, resolved)
                    VALUES (NOW(), ?, ?, ?, ?, ?, true)
                    """,
                    geo.state(), radius, facilities.size(),
                    nearest < 0 ? null : nearest,
                    geo.geocoder());
        } catch (Exception ignored) {
            // audit logging should never break the user request
        }
    }
}
