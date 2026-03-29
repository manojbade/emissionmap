package com.emissionmap.domain.view;

import java.util.List;

public record FacilityTableRow(
        int year,
        List<Double> chemicalTotals,
        double totalReleaseLbs
) {}
