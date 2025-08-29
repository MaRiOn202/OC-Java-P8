package com.openclassrooms.tourguide.service;


import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


/**
 *  Service permettant de gérer les récompenses
 *  Il calcule les récompses attribuées aux users en fonction des attractions visitées
 *  et de la proximité configurée.
 *
 */
@Slf4j
//@Service
public class RewardsService {

	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles  pour valider les points et la reward ajoutée et des true pour isNearAttraction - 10 à la base
	private int defaultProximityBuffer = 200;

	@Setter
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	private final ExecutorService ex = Executors.newFixedThreadPool(100);


	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}


	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}


	/**
	 *
	 *  Méthode permettant de calculer les récompenses d'un user
	 *  Pour chaque position visitée par le user, elle vérifie quelles attractions
	 *  sont suffisamment proches (selon un seuil de proximité configurable).
	 *  Pour ces attractions, elle récupère les points de récompense via RewardCentral et ajoute
	 *  une récompense utilisateur si elle n'existe pas déjà.
	 *
	 * Le calcul est réalisé de manière asynchrone dans un thread dédié.
	 *
	 * @param user
	 * @return CompletableFuture
	 */
	public CompletableFuture<Void> calculateRewards(User user) {

		// Lancement calculs des rewards en asynchrone - promesse
		return CompletableFuture.runAsync(() -> {

			// Récupération des données
			List<VisitedLocation> userLocations = user.getVisitedLocations();
			log.info("Rewards user : {}", user.getUserRewards());
			List<Attraction> attractions = gpsUtil.getAttractions();

			log.info("Calcul des rewards pour le user '{}' ", user.getUserName());
			log.info("Nbre total de lieux visités : {}", userLocations.size());
			log.info("Nbre total d'attractions : {}", attractions.size());

			// Enumération des rewards à récupérer
			Map<VisitedLocation, List<Attraction>> map = new HashMap<>();

			//Pr chque lieu que le user a visité, on parcourt ttes ls attractions
			for(VisitedLocation visitedLocation : userLocations) {
				for(Attraction attraction : attractions) {
					log.info("VisitedLocation : {} , attraction : {}", visitedLocation, attraction );

					if( user.getUserRewards().isEmpty() || !user
							.getUserRewards()
							.stream()
							.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))  // Si pas encore de rewards alors
/*							.filter(r -> r.attraction.attractionName.equals(attraction.attractionName))
							.count() == 0)*/ {

						Boolean isNearAttraction = nearAttraction(visitedLocation, attraction);
						log.info("isNerAttraction : {}", isNearAttraction);
						if(nearAttraction(visitedLocation, attraction)) {
							map.computeIfAbsent(visitedLocation, k -> new ArrayList<>()).add(attraction);  // si map ne contient pas encore la visitedLocation alors new ArrayList
						}
					}
				}
			}

			log.info("Map  : {} ", map.entrySet() );

			// Enfin calcul des points et ajout des rewards
			for (Map.Entry<VisitedLocation, List<Attraction>> entry : map.entrySet()) {
				VisitedLocation visitedLocation = entry.getKey();
				log.info("List des attraction : {}", entry.getValue() );
				log.info("List des visitedLocation : {}", entry.getKey() );
				for (Attraction attraction : entry.getValue()) {
					try {
						log.info("Avant int point");

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


	/**
	 *  Méthode permettant de vérifier si une localisation est dans la zone de proximité d'une attraction.
	 *  Méthode utilisée pour valider les récompenses ou non
	 *
	 * @param attraction
	 * @param location
	 * @return boolean - true si la localisation est dans la zone de proximité, sinon false
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}


	/**
	 *  Méthode permettant de vérifier si un user est à proximité d'une attraction donnée
	 *
	 * @param visitedLocation
	 * @param attraction
	 * @return retourne true si la localisation est à moins de la proximité configurée, sinon false
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		//return true;   // test
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}


	/**
	 *  Méthode permettant de récupérer le nombre de pts de récompense pour une attraction donnée et un user.
	 *  Méthode asynchrone.
	 *
	 * @param attraction
	 * @param user
	 * @return CompletableFuture
	 */
	public CompletableFuture<Integer> getRewardPoints(Attraction attraction, User user) {
		return CompletableFuture.supplyAsync(() ->
				rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()));
	}


	/**
	 *  Méthode permettant de calculer la distance en miles entre deux points géographiques.
	 *
	 * @param loc1
	 * @param loc2
	 * @return statuteMiles - la distance entre les deux points
	 */
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


	/**
	 * Arrêt propre du service lors de l'arrêt de l'application,
	 * en stoppant l'ExecutorService utilisé pour les threads asynchrones
	 *
	 */
	@PreDestroy
	public void shutDown() {
		log.info("Arrêt du RewardsService : fin du thread");
		ex.shutdown();
	}


}
