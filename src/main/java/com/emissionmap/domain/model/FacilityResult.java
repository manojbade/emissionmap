package com.emissionmap.domain.model;

import java.util.List;

public record FacilityResult(
        String facilityId,
        String name,
        String parentCo,
        String street,
        String city,
        String state,
        double lat,
        double lng,
        double distanceMiles,
        double totalReleaseLbs,
        int year,
        List<ChemicalRelease> chemicals
) {
    public boolean hasCarcinogen() {
        return chemicals.stream().anyMatch(ChemicalRelease::carcinogen);
    }

    public boolean hasPfas() {
        return chemicals.stream().anyMatch(ChemicalRelease::pfas);
    }

    public boolean hasPbt() {
        return chemicals.stream().anyMatch(ChemicalRelease::pbt);
    }

    public String echoUrl() {
        return "https://echo.epa.gov/detailed-facility-report?fid=" + facilityId;
    }
}
