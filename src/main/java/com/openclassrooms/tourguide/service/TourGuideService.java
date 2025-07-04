package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearByAttractionDto;
import com.openclassrooms.tourguide.exception.LocationNotFoundException;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {


	private final Logger log= LoggerFactory.getLogger(TourGuideService.class);

	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;


	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			log.info("TestMode enabled");
			log.debug("Initializing users");
			initializeInternalUsers();
			log.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	// à faire
	public List<UserReward> getUserRewards(User user) {

		return user.getUserRewards();
	}

	// à relire voir pour l'utilisation de la variable optimal ?
	public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation;
        try {
            visitedLocation = (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation()
                    : trackUserLocation(user).get();
        } catch (ExecutionException | InterruptedException e) {
			log.info("Echec lors de la récupération de la position de l'utilisateur");
            throw new LocationNotFoundException("Erreur lors de la récupération de la position de l'utilisateur.", e);
        }
        return visitedLocation;
    }

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return
                new ArrayList<>(internalUserMap
                        .values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	// à relire - optimal ?
	private final Executor ex = Executors.newFixedThreadPool(100);

	public CompletableFuture<VisitedLocation> trackUserLocation(User user)   {

		return CompletableFuture.supplyAsync(() -> {

		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		try {
			log.info("Avant le calcul des rewards pour '{}'", user.getUserName());
			rewardsService.calculateRewards(user).get(); // arrête l'asynchrone - récupère donnée
			log.info("Après le calcul des rewards pour '{}'", user.getUserName());
		} catch (ExecutionException  | InterruptedException e ){
			throw new RuntimeException(e);
		}

		return visitedLocation;
		}, ex);
	}


	// Etape 3 : 5 attractions les + proches
	public List<NearByAttractionDto> getNearByAttractions(VisitedLocation visitedLocation, User user) {

		// Localisation du user
		Location locationOfUser = visitedLocation.location;

		// On doit parcourir ttes les attractions avec stream 26 in gpsUtil
		List<NearByAttractionDto> nearAttractionList =  gpsUtil.getAttractions()
				.stream()
				.map(attraction -> {
					double distanceBetweenAttractionsAndUser = rewardsService.getDistance(locationOfUser, attraction);

					// On calcule les points attribués à une attraction et un user
					int rewardPoints;
					try {
						rewardPoints = rewardsService.getRewardPoints(attraction, user).get();
					} catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }

					// retourner attractionName, latitude longitude user longitude latitude disantce et les points
                    return new NearByAttractionDto(
							attraction.attractionName,
							attraction.latitude,
							attraction.longitude,
							locationOfUser.latitude,
							locationOfUser.longitude,
							distanceBetweenAttractionsAndUser,
							rewardPoints
					);
                }).sorted(Comparator.comparing(nearByAttractionDto ->
						nearByAttractionDto.distanceMiles)).limit(5).toList();

        return nearAttractionList;
    }



	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}





	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			log.info("Utilisateur internet initialisé : {}", userName );
			internalUserMap.put(userName, user);
		});
		log.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
