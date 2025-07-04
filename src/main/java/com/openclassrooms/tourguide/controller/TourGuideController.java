package com.openclassrooms.tourguide.controller;

import java.util.List;

import com.openclassrooms.tourguide.dto.NearByAttractionDto;
import com.openclassrooms.tourguide.exception.LocationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import org.springframework.web.server.ResponseStatusException;
import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    private Logger log = LoggerFactory.getLogger(TourGuideController.class);


	
    @GetMapping("/")
    public String index() {

        return "Greetings from TourGuide!";
    }
    
    @GetMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName)  {
        // changé
/*        User user = tourGuideService.getUser(userName);
        if(user == null) {
            throw new LocationNotFoundException("Utilisateur non trouvé: " + userName, null);
        }
        return tourGuideService.getUserLocation(user);*/
        return tourGuideService.getUserLocation(getUser(userName));

    }
    
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral
    @GetMapping("/getNearByAttractions")
    public List<NearByAttractionDto> getNearByAttractions(@RequestParam String userName) {

        User user = tourGuideService.getUser(userName);
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
        List<NearByAttractionDto> nearByAttractionDtoList = tourGuideService.getNearByAttractions(visitedLocation, user);
        log.info("La liste des 5 attractions les plus proches de {} : {} ", user.getUserName(), nearByAttractionDtoList);
    	return nearByAttractionDtoList;
    }


    
    @GetMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        User user = tourGuideService.getUser(userName);
        List<UserReward> userRewardList = tourGuideService.getUserRewards(user);
    	 return userRewardList;
    }



       
    @GetMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }

    @GetMapping("/getUser")
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}