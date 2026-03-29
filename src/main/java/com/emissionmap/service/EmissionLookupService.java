package com.emissionmap.service;

import com.emissionmap.domain.model.ChemicalRelease;
import com.emissionmap.domain.model.FacilityResult;
import com.emissionmap.domain.model.GeocodedAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmissionLookupService {

    private static final double METERS_PER_MILE = 1609.344;

    private final JdbcTemplate jdbc;
    private final GeocodingService geocodingService;

    public EmissionLookupService(JdbcTemplate jdbc, GeocodingService geocodingService) {
        this.jdbc = jdbc;
        this.geocodingService = geocodingService;
    }

    public List<FacilityResult> lookup(String address, int radiusMiles, int year) {
        GeocodedAddress geo = geocodingService.geocode(address);
        return lookupByCoords(geo, radiusMiles, year);
    }

    public List<FacilityResult> lookupByCoords(GeocodedAddress geo, int radiusMiles, int year) {
        double radiusMeters = radiusMiles * METERS_PER_MILE;

        // Step 1: find facilities within radius
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
                ORDER BY dist_meters ASC
                """;

        record FacilityRow(String id, String name, String parentCo, String street, String city,
                           String state, double lat, double lng, double distMeters) {}

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
                geo.lng(), geo.lat(), geo.lng(), geo.lat(), radiusMeters
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
}
