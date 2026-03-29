package com.emissionmap.domain.view;

import java.util.Map;

public record FacilityChemicalSummary(
        String chemical,
        String casNumber,
        boolean carcinogen,
        boolean pfas,
        boolean pbt,
        double totalReleaseLbs,
        Map<Integer, Double> yearlyTotals
) {}
