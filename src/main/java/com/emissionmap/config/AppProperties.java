package com.emissionmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "emissionmap")
public class AppProperties {

    private final Data data = new Data();
    private final Geocoder geocoder = new Geocoder();
    private final Lookup lookup = new Lookup();

    public Data getData() { return data; }
    public Geocoder getGeocoder() { return geocoder; }
    public Lookup getLookup() { return lookup; }

    public static class Data {
        /** Absolute path to the folder containing TRI CSV files */
        private String dir = "./data";
        /** Years to load on bootstrap (comma-separated, e.g. 2022,2023,2024) */
        private List<Integer> years = List.of(2022, 2023, 2024);
        private boolean bootstrapEnabled = false;

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public List<Integer> getYears() { return years; }
        public void setYears(List<Integer> years) { this.years = years; }
        public boolean isBootstrapEnabled() { return bootstrapEnabled; }
        public void setBootstrapEnabled(boolean bootstrapEnabled) { this.bootstrapEnabled = bootstrapEnabled; }
    }

    public static class Geocoder {
        private String censusBaseUrl = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress";
        private String censusBenchmark = "Public_AR_Current";
        private Duration censusTimeout = Duration.ofSeconds(5);
        private String nominatimBaseUrl = "https://nominatim.openstreetmap.org/search";
        private int nominatimLimit = 1;
        private Duration nominatimTimeout = Duration.ofSeconds(20);
        private String userAgent = "emissionmap/0.0.1";

        public String getCensusBaseUrl() { return censusBaseUrl; }
        public void setCensusBaseUrl(String v) { this.censusBaseUrl = v; }
        public String getCensusBenchmark() { return censusBenchmark; }
        public void setCensusBenchmark(String v) { this.censusBenchmark = v; }
        public Duration getCensusTimeout() { return censusTimeout; }
        public void setCensusTimeout(Duration v) { this.censusTimeout = v; }
        public String getNominatimBaseUrl() { return nominatimBaseUrl; }
        public void setNominatimBaseUrl(String v) { this.nominatimBaseUrl = v; }
        public int getNominatimLimit() { return nominatimLimit; }
        public void setNominatimLimit(int v) { this.nominatimLimit = v; }
        public Duration getNominatimTimeout() { return nominatimTimeout; }
        public void setNominatimTimeout(Duration v) { this.nominatimTimeout = v; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String v) { this.userAgent = v; }
    }

    public static class Lookup {
        private int defaultRadiusMiles = 3;
        private int maxRadiusMiles = 10;
        private int defaultYear = 2024;
        private List<String> excludedFacilityIds = List.of();

        public int getDefaultRadiusMiles() { return defaultRadiusMiles; }
        public void setDefaultRadiusMiles(int v) { this.defaultRadiusMiles = v; }
        public int getMaxRadiusMiles() { return maxRadiusMiles; }
        public void setMaxRadiusMiles(int v) { this.maxRadiusMiles = v; }
        public int getDefaultYear() { return defaultYear; }
        public void setDefaultYear(int v) { this.defaultYear = v; }
        public List<String> getExcludedFacilityIds() { return excludedFacilityIds; }
        public void setExcludedFacilityIds(List<String> excludedFacilityIds) {
            this.excludedFacilityIds = excludedFacilityIds == null ? List.of() : excludedFacilityIds;
        }
    }
}
