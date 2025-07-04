package com.openclassrooms.tourguide.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


// Etape 3 : 5 attractions les + proches

@Setter
@Getter
@ToString
@AllArgsConstructor
public class NearByAttractionDto {

    public String attractionName;

    public double attractionLatitude;

    public double attractionLongitude;

    public double userLatitude;

    public double userLongitude;

    public double distanceMiles;

    public int rewardPoints;






}
