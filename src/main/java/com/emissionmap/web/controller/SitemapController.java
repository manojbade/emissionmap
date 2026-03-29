package com.emissionmap.web.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SitemapController {

    private static final String BASE_URL = "https://emissionmap.com";

    private final JdbcTemplate jdbc;

    public SitemapController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    @ResponseBody
    public String sitemap() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<String> urls = new ArrayList<>();
        urls.add(BASE_URL + "/");
        urls.add(BASE_URL + "/about");

        try {
            urls.addAll(jdbc.query(
                    "SELECT facility_id FROM tri_facilities ORDER BY facility_id",
                    (rs, rowNum) -> BASE_URL + "/facility/" + escapeXml(rs.getString("facility_id"))
            ));
        } catch (DataAccessException ignored) {
            // Static entries are still useful in profiles without TRI tables.
        }

        String lastmod = today.toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (String url : urls) {
            xml.append("<url>");
            xml.append("<loc>").append(url).append("</loc>");
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
            xml.append("</url>");
        }
        xml.append("</urlset>");
        return xml.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
