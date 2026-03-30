package com.emissionmap.service;

import com.emissionmap.config.AppProperties;
import com.emissionmap.domain.model.ChemicalRelease;
import com.emissionmap.domain.model.FacilityResult;
import com.emissionmap.domain.model.GeocodedAddress;
import com.emissionmap.domain.view.FacilityChemicalSummary;
import com.emissionmap.domain.view.FacilityTableRow;
import com.emissionmap.domain.view.FacilityViewModel;
import com.emissionmap.domain.view.FacilityYearSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class EmissionLookupService {

    private static final double METERS_PER_MILE = 1609.344;

    private final JdbcTemplate jdbc;
    private final GeocodingService geocodingService;
    private final AppProperties.Lookup lookupProperties;

    public EmissionLookupService(JdbcTemplate jdbc,
                                 GeocodingService geocodingService,
                                 AppProperties appProperties) {
        this.jdbc = jdbc;
        this.geocodingService = geocodingService;
        this.lookupProperties = appProperties.getLookup();
    }

    public List<FacilityResult> lookup(String address, int radiusMiles, int year) {
        GeocodedAddress geo = geocodingService.geocode(address);
        return lookupByCoords(geo, radiusMiles, year);
    }

    public List<FacilityResult> lookupByCoords(GeocodedAddress geo, int radiusMiles, int year) {
        double radiusMeters = radiusMiles * METERS_PER_MILE;
        List<String> excludedFacilityIds = lookupProperties.getExcludedFacilityIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();

        // Step 1: find facilities within radius
        String exclusionClause = "";
        if (!excludedFacilityIds.isEmpty()) {
            String placeholders = excludedFacilityIds.stream()
                    .map(ignored -> "?")
                    .collect(Collectors.joining(", "));
            exclusionClause = "\n  AND f.facility_id NOT IN (" + placeholders + ")";
        }

        String facilitySql = """
                SELECT f.facility_id,
                       f.name,
                       f.parent_co,
                       f.street,
                       f.city,
                       f.state,
                       f.lat,
                       f.lng,
                       ST_Distance(
                           f.geom::geography,
                           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                       ) AS dist_meters
                FROM tri_facilities f
                WHERE ST_DWithin(
                    f.geom::geography,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?
                )
                %s
                ORDER BY dist_meters ASC
                """.formatted(exclusionClause);

        record FacilityRow(String id, String name, String parentCo, String street, String city,
                           String state, double lat, double lng, double distMeters) {}

        List<Object> facilityParams = new ArrayList<>();
        facilityParams.add(geo.lng());
        facilityParams.add(geo.lat());
        facilityParams.add(geo.lng());
        facilityParams.add(geo.lat());
        facilityParams.add(radiusMeters);
        facilityParams.addAll(excludedFacilityIds);

        List<FacilityRow> facilityRows = jdbc.query(facilitySql,
                (rs, i) -> new FacilityRow(
                        rs.getString("facility_id"),
                        rs.getString("name"),
                        rs.getString("parent_co"),
                        rs.getString("street"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("dist_meters")
                ),
                facilityParams.toArray()
        );

        if (facilityRows.isEmpty()) return List.of();

        // Step 2: load chemicals for those facilities in the requested year
        List<String> ids = facilityRows.stream().map(FacilityRow::id).toList();
        String inClause = "?,".repeat(ids.size()).replaceAll(",$", "");

        String chemSql = String.format("""
                SELECT facility_id,
                       chemical,
                       cas_number,
                       carcinogen,
                       pfas,
                       pbt,
                       air_fugitive,
                       air_stack,
                       water,
                       on_site_total,
                       unit_of_measure
                FROM tri_releases
                WHERE facility_id IN (%s)
                  AND year = ?
                  AND on_site_total > 0
                ORDER BY on_site_total DESC
                """, inClause);

        Object[] chemParams = new Object[ids.size() + 1];
        for (int i = 0; i < ids.size(); i++) chemParams[i] = ids.get(i);
        chemParams[ids.size()] = year;

        Map<String, List<ChemicalRelease>> chemsByFacility = new LinkedHashMap<>();
        jdbc.query(chemSql, (rs) -> {
            String fid = rs.getString("facility_id");
            ChemicalRelease chem = new ChemicalRelease(
                    rs.getString("chemical"),
                    rs.getString("cas_number"),
                    rs.getBoolean("carcinogen"),
                    rs.getBoolean("pfas"),
                    rs.getBoolean("pbt"),
                    rs.getDouble("air_fugitive"),
                    rs.getDouble("air_stack"),
                    rs.getDouble("water"),
                    rs.getDouble("on_site_total"),
                    rs.getString("unit_of_measure")
            );
            chemsByFacility.computeIfAbsent(fid, k -> new ArrayList<>()).add(chem);
        }, chemParams);

        // Step 3: assemble results ordered by total release descending
        List<FacilityResult> results = new ArrayList<>();
        for (FacilityRow f : facilityRows) {
            List<ChemicalRelease> chems = chemsByFacility.getOrDefault(f.id(), List.of());
            double total = chems.stream().mapToDouble(ChemicalRelease::onSiteTotal).sum();
            if (total == 0 && chems.isEmpty()) continue; // facility didn't report in this year
            results.add(new FacilityResult(
                    f.id(), f.name(), f.parentCo(), f.street(), f.city(), f.state(),
                    f.lat(), f.lng(),
                    f.distMeters() / METERS_PER_MILE,
                    total,
                    year,
                    chems
            ));
        }

        results.sort((a, b) -> Double.compare(b.totalReleaseLbs(), a.totalReleaseLbs()));
        return results;
    }

    public Optional<FacilityViewModel> loadFacilityDetails(String facilityId) {
        String facilitySql = """
                SELECT facility_id, name, parent_co, street, city, state, zip
                FROM tri_facilities
                WHERE facility_id = ?
                """;

        record FacilityMetadata(
                String facilityId,
                String name,
                String parentCo,
                String street,
                String city,
                String state,
                String zip
        ) {}

        List<FacilityMetadata> facilities = jdbc.query(facilitySql,
                (rs, i) -> new FacilityMetadata(
                        rs.getString("facility_id"),
                        rs.getString("name"),
                        rs.getString("parent_co"),
                        rs.getString("street"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("zip")
                ),
                facilityId
        );

        if (facilities.isEmpty()) {
            return Optional.empty();
        }

        String releaseSql = """
                SELECT year,
                       chemical,
                       cas_number,
                       carcinogen,
                       pfas,
                       pbt,
                       COALESCE(on_site_total, 0) AS on_site_total
                FROM tri_releases
                WHERE facility_id = ?
                  AND COALESCE(on_site_total, 0) > 0
                ORDER BY year DESC, on_site_total DESC, chemical ASC
                """;

        record ReleaseRow(
                int year,
                String chemical,
                String casNumber,
                boolean carcinogen,
                boolean pfas,
                boolean pbt,
                double totalReleaseLbs
        ) {}

        List<ReleaseRow> releases = jdbc.query(releaseSql,
                (rs, i) -> new ReleaseRow(
                        rs.getInt("year"),
                        rs.getString("chemical"),
                        rs.getString("cas_number"),
                        rs.getBoolean("carcinogen"),
                        rs.getBoolean("pfas"),
                        rs.getBoolean("pbt"),
                        rs.getDouble("on_site_total")
                ),
                facilityId
        );

        FacilityMetadata facility = facilities.get(0);

        if (releases.isEmpty()) {
            return Optional.of(new FacilityViewModel(
                    facility.facilityId(),
                    facility.name(),
                    facility.parentCo(),
                    facility.street(),
                    facility.city(),
                    facility.state(),
                    facility.zip(),
                    false,
                    false,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null
            ));
        }

        Map<Integer, Double> totalsByYear = new TreeMap<>(Comparator.reverseOrder());
        Map<String, ChemicalAccumulator> chemicalsByName = new LinkedHashMap<>();

        boolean hasCarcinogen = false;
        boolean hasPfas = false;
        boolean hasPbt = false;

        for (ReleaseRow release : releases) {
            totalsByYear.merge(release.year(), release.totalReleaseLbs(), Double::sum);

            ChemicalAccumulator accumulator = chemicalsByName.computeIfAbsent(
                    release.chemical(),
                    ignored -> new ChemicalAccumulator(release.chemical(), release.casNumber())
            );
            accumulator.add(release.year(), release.totalReleaseLbs(), release.carcinogen(), release.pfas(), release.pbt());

            hasCarcinogen |= release.carcinogen();
            hasPfas |= release.pfas();
            hasPbt |= release.pbt();
        }

        List<FacilityYearSummary> yearlyReleases = totalsByYear.entrySet().stream()
                .map(entry -> new FacilityYearSummary(entry.getKey(), entry.getValue()))
                .toList();

        List<FacilityChemicalSummary> chemicalSummaries = chemicalsByName.values().stream()
                .map(ChemicalAccumulator::toSummary)
                .sorted(Comparator
                        .comparingDouble(FacilityChemicalSummary::totalReleaseLbs).reversed()
                        .thenComparing(FacilityChemicalSummary::chemical))
                .toList();

        List<FacilityChemicalSummary> topChemicals = chemicalSummaries.stream().limit(10).toList();
        List<String> tableChemicalColumns = chemicalSummaries.stream()
                .limit(5)
                .map(FacilityChemicalSummary::chemical)
                .toList();

        List<FacilityTableRow> tableRows = yearlyReleases.stream()
                .map(summary -> new FacilityTableRow(
                        summary.year(),
                        tableChemicalColumns.stream()
                                .map(name -> chemicalsByName.get(name).yearlyTotal(summary.year()))
                                .toList(),
                        summary.totalReleaseLbs()
                ))
                .toList();

        return Optional.of(new FacilityViewModel(
                facility.facilityId(),
                facility.name(),
                facility.parentCo(),
                facility.street(),
                facility.city(),
                facility.state(),
                facility.zip(),
                hasCarcinogen,
                hasPfas,
                hasPbt,
                yearlyReleases,
                chemicalSummaries,
                topChemicals,
                tableChemicalColumns,
                tableRows,
                null
        ));
    }

    private static final class ChemicalAccumulator {
        private final String chemical;
        private final String casNumber;
        private final Map<Integer, Double> yearlyTotals = new TreeMap<>();
        private boolean carcinogen;
        private boolean pfas;
        private boolean pbt;
        private double totalReleaseLbs;

        private ChemicalAccumulator(String chemical, String casNumber) {
            this.chemical = chemical;
            this.casNumber = casNumber;
        }

        private void add(int year, double lbs, boolean carcinogen, boolean pfas, boolean pbt) {
            yearlyTotals.merge(year, lbs, Double::sum);
            totalReleaseLbs += lbs;
            this.carcinogen |= carcinogen;
            this.pfas |= pfas;
            this.pbt |= pbt;
        }

        private double yearlyTotal(int year) {
            return yearlyTotals.getOrDefault(year, 0.0);
        }

        private FacilityChemicalSummary toSummary() {
            return new FacilityChemicalSummary(
                    chemical,
                    casNumber,
                    carcinogen,
                    pfas,
                    pbt,
                    totalReleaseLbs,
                    Map.copyOf(yearlyTotals)
            );
        }
    }
}
