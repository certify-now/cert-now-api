package com.uk.certifynow.certify_now.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PropertyType {

    FLAT("Flat"),
    TERRACED("Terraced"),
    SEMI_DETACHED("Semi-Detached"),
    DETACHED("Detached"),
    BUNGALOW("Bungalow"),
    MAISONETTE("Maisonette"),
    COMMERCIAL("Commercial"),
    OTHER("Other"),
    HMO("HMO");

    private final String propertyType;
}
