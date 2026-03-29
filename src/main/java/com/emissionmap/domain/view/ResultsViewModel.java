package com.emissionmap.domain.view;

import com.emissionmap.domain.model.FacilityResult;
import com.emissionmap.domain.model.GeocodedAddress;

import java.util.List;

public record ResultsViewModel(
        GeocodedAddress geocodedAddress,
        List<FacilityResult> facilities,
        int radiusMiles,
        int year,
        String error
) {
    public boolean hasError() {
        return error != null && !error.isBlank();
    }

    public boolean hasResults() {
        return facilities != null && !facilities.isEmpty();
    }

    public static ResultsViewModel error(String address, String message) {
        return new ResultsViewModel(
                new GeocodedAddress(address, null, null, null, null, null, 0, 0),
                List.of(), 3, 2024, message
        );
    }
}
