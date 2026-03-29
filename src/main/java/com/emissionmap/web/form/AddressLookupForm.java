package com.emissionmap.web.form;

import jakarta.validation.constraints.NotBlank;

public class AddressLookupForm {

    @NotBlank(message = "Please enter an address")
    private String address;

    private int radius = 3;
    private int year = 2024;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
}
