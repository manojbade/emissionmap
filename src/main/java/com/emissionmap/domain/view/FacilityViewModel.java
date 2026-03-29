package com.emissionmap.domain.view;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public record FacilityViewModel(
        String facilityId,
        String name,
        String parentCo,
        String street,
        String city,
        String state,
        String zip,
        boolean hasCarcinogen,
        boolean hasPfas,
        boolean hasPbt,
        List<FacilityYearSummary> yearlyReleases,
        List<FacilityChemicalSummary> chemicalSummaries,
        List<FacilityChemicalSummary> topChemicals,
        List<String> tableChemicalColumns,
        List<FacilityTableRow> tableRows,
        String error
) {
    public boolean hasError() {
        return error != null && !error.isBlank();
    }

    public boolean hasData() {
        return yearlyReleases != null && !yearlyReleases.isEmpty();
    }

    public String addressLine() {
        return Stream.of(street, city, state, zip)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    public String echoUrl() {
        return "https://echo.epa.gov/detailed-facility-report?fid=" + facilityId;
    }

    public static FacilityViewModel error(String facilityId, String message) {
        return new FacilityViewModel(
                facilityId,
                "Facility details unavailable",
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                message
        );
    }
}
