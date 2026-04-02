package com.emissionmap.web.controller;

import com.emissionmap.web.form.AddressLookupForm;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final Environment env;

    public HomeController(Environment env) {
        this.env = env;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("form", new AddressLookupForm());
        model.addAttribute("mapboxToken", env.getProperty("MAPBOX_PUBLIC_TOKEN", ""));
        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
