package com.emissionmap.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final JdbcTemplate jdbc;

    public FeedbackController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/feedback")
    public String submit(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) Boolean helpful,
            @RequestParam(required = false) String comment) {
        try {
            String trimmedComment = (comment != null && !comment.isBlank())
                    ? comment.trim().substring(0, Math.min(comment.trim().length(), 500))
                    : null;
            jdbc.update(
                    "INSERT INTO feedback (submitted_at, page, helpful, comment) VALUES (NOW(), ?, ?, ?)",
                    page, helpful, trimmedComment);
        } catch (Exception e) {
            log.warn("Feedback insert failed: {}", e.getMessage());
        }
        return "redirect:/";
    }
}
