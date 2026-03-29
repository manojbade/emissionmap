package com.emissionmap.domain.model;

public record GeocodedAddress(
        String rawInput,
        String matchedAddress,
        String city,
        String state,
        String zip,
        String geocoder,
        double lat,
        double lng
) {}
