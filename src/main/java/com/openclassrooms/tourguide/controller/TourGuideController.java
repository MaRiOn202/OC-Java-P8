package com.openclassrooms.tourguide.controller;

import java.util.List;

import com.openclassrooms.tourguide.dto.NearByAttractionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;


/**
 *  Controller REST permettant de gérer les requêtes
 *  Il expose des endpoints pour récupérer la localisation actuelle d'un user,
 *  les attractions les plus proches, les récompenses obtenues, les offres de voyage personnalisées,
 *  ou encore les informations d'un user
 *
 */
@RestController
public class TourGuideController {


	private final TourGuideService tourGuideService;


    private Logger log = LoggerFactory.getLogger(TourGuideController.class);

    public TourGuideController(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }


    /**
     *  Endpoint home de l'application
     *
     * @return un message de bienvenue
     */
    @GetMapping("/")
    public String index() {

        return "Greetings from TourGuide!";
    }


    /**
     *  Récupère la dernière position connue de l'utilisateur.
     *
     * @param userName
     * @return la dernière localisation visitée
     */
    @GetMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName)  {
        return tourGuideService.getUserLocation(getUser(userName));

    }


    /**
     *  Récupère les 5 attractions les plus proches de l'utilisateur
     *
     * @param userName
     * @return une liste de 5 attractions
     */
    @GetMapping("/getNearByAttractions")
    public List<NearByAttractionDto> getNearByAttractions(@RequestParam String userName) {

        User user = tourGuideService.getUser(userName);
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);
        List<NearByAttractionDto> nearByAttractionDtoList = tourGuideService.getNearByAttractions(visitedLocation, user);
        log.info("La liste des 5 attractions les plus proches de {} : {} ", user.getUserName(), nearByAttractionDtoList);
    	return nearByAttractionDtoList;
    }


    /**
     *  Récupère toutes les récompenses obtenues par l'utilisateur
     *
     * @param userName
     * @return une liste de UserReward
     */
    @GetMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        User user = tourGuideService.getUser(userName);
        List<UserReward> userRewardList = tourGuideService.getUserRewards(user);
        return userRewardList;
    }


    /**
     *  Récupère les offres de voyage personnalisées pour l'utilisateur
     *
     * @param userName
     * @return une liste de Provider / fournisseurs
     */
    @GetMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }



    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}