package com.emissionmap.domain.model;

public record ChemicalRelease(
        String chemical,
        String casNumber,
        boolean carcinogen,
        boolean pfas,
        boolean pbt,
        double airFugitive,
        double airStack,
        double water,
        double onSiteTotal,
        String unitOfMeasure
) {
    public double totalAir() {
        return airFugitive + airStack;
    }

    public String primaryPathway() {
        double air = totalAir();
        if (air >= water && air >= 0) return "Air";
        if (water > air) return "Water";
        return "Air";
    }
}
