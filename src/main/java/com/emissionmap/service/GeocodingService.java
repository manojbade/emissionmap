package com.emissionmap.service;

import com.emissionmap.config.AppProperties;
import com.emissionmap.domain.model.GeocodedAddress;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class GeocodingService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.Geocoder props;

    public GeocodingService(HttpClient httpClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.props = appProperties.getGeocoder();
    }

    public GeocodedAddress geocode(String rawAddress) {
        String address = stripCountrySuffix(rawAddress);

        GeocodedAddress census = geocodeWithCensus(address);
        if (census != null) return census;

        GeocodedAddress nominatim = geocodeWithNominatim(address);
        if (nominatim != null) return nominatim;

        throw new IllegalArgumentException("Unable to geocode address: " + rawAddress);
    }

    private String stripCountrySuffix(String address) {
        if (address == null) return null;
        return address
                .replaceAll("(?i),?\\s*United States of America\\s*$", "")
                .replaceAll("(?i),?\\s*United States\\s*$", "")
                .replaceAll("(?i),?\\s*\\bUSA\\b\\s*$", "")
                .replaceAll("(?i),?\\s*\\bUS\\b\\s*$", "")
                .trim();
    }

    private GeocodedAddress geocodeWithCensus(String address) {
        try {
            String url = props.getCensusBaseUrl()
                    + "?benchmark=" + URLEncoder.encode(props.getCensusBenchmark(), StandardCharsets.UTF_8)
                    + "&format=json&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(props.getCensusTimeout())
                    .header("User-Agent", props.getUserAgent())
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode matches = root.path("result").path("addressMatches");
            if (!matches.isArray() || matches.isEmpty()) return null;
            JsonNode best = matches.get(0);
            JsonNode components = best.path("addressComponents");
            return new GeocodedAddress(
                    address,
                    best.path("matchedAddress").asText(address),
                    text(components, "city"),
                    text(components, "state"),
                    text(components, "zip"),
                    "CENSUS",
                    number(best.path("coordinates"), "y"),
                    number(best.path("coordinates"), "x")
            );
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private GeocodedAddress geocodeWithNominatim(String address) {
        try {
            String url = props.getNominatimBaseUrl()
                    + "?format=jsonv2&limit=" + props.getNominatimLimit()
                    + "&addressdetails=1&q=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(props.getNominatimTimeout())
                    .header("User-Agent", props.getUserAgent())
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) return null;
            JsonNode best = root.get(0);
            JsonNode addr = best.path("address");
            return new GeocodedAddress(
                    address,
                    best.path("display_name").asText(address),
                    text(addr, "city", "town", "village"),
                    text(addr, "state"),
                    text(addr, "postcode"),
                    "NOMINATIM",
                    decimal(best, "lat"),
                    decimal(best, "lon")
            );
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.path(field);
            if (!child.isMissingNode() && !child.asText().isBlank()) return child.asText();
        }
        return null;
    }

    private double number(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() ? 0.0 : child.asDouble();
    }

    private double decimal(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) return 0.0;
        try { return Double.parseDouble(child.asText()); } catch (NumberFormatException e) { return 0.0; }
    }
}
