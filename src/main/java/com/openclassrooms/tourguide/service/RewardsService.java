package com.openclassrooms.tourguide.service;


import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


@Slf4j
//@Service
@AllArgsConstructor
public class RewardsService {

	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	@Setter
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}


	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}



	private Executor ex = Executors.newFixedThreadPool(50);

	public CompletableFuture<Void> calculateRewards(User user) {

		// Lancement calculs asynchrone - promesse
		return CompletableFuture.runAsync(() -> {

			List<VisitedLocation> userLocations = user.getVisitedLocations();
			List<Attraction> attractions = gpsUtil.getAttractions();


			log.info("Calcul des rewards pour le user '{}' ", user.getUserName());
			log.info("Nbre total de lieux visités : {}", userLocations.size());
			log.info("Nbre total d'attractions : {}", attractions.size());

			Map<VisitedLocation, List<Attraction>> map = new HashMap<>();

			//Pr chque lieu que le user a visité, on parcourt ttes ls attractions
			for(VisitedLocation visitedLocation : userLocations) {
				for(Attraction attraction : attractions) {

					if(user
							.getUserRewards()
							.stream()
							.filter(r -> r.attraction.attractionName.equals(attraction.attractionName))
							.count() == 0) {
						if(nearAttraction(visitedLocation, attraction)) {
							map.computeIfAbsent(visitedLocation, k -> new ArrayList<>()).add(attraction);
						}
					}
				}
			}
			//
			for (Map.Entry<VisitedLocation, List<Attraction>> entry : map.entrySet()) {
				VisitedLocation visitedLocation = entry.getKey();
				for (Attraction attraction : entry.getValue()) {
					try {
						int point = getRewardPoints(attraction, user).get();
						user.addUserReward(new UserReward(visitedLocation, attraction, point));
						log.info("La reward ajoutée : {} ({}, {})", attraction.attractionName, attraction.latitude, attraction.longitude);
						log.info("Nbre total de rewards pour '{}' : {}, points = {} ", user.getUserName() ,user.getUserRewards().size(), point);
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
				}
            }
			log.info("La méthode CalculateRewards() est terminée pour user '{}'", user.getUserName());

		}, ex);

	}


	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		//return true;   // test
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	public CompletableFuture<Integer> getRewardPoints(Attraction attraction, User user) {
		return CompletableFuture.supplyAsync(() ->
				rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()));
	}


	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}


}
