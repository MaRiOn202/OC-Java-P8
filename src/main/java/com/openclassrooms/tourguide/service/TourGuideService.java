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
import java.util.concurrent.*;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;




/**
 *  Service permettant de gérer les users, leur localisation ainsi que les points de récompense
 *  Fin du service, initialisation des users pour les tests
 *
 *
 */
@Service
public class TourGuideService {


	private final Logger log= LoggerFactory.getLogger(TourGuideService.class);

	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	private final ExecutorService ex = Executors.newFixedThreadPool(100);


	public TourGuideService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = new RewardsService(gpsUtil, rewardCentral);

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


	public List<UserReward> getUserRewards(User user) {

		return user.getUserRewards();
	}


	// à relire voir pour l'utilisation de la variable optimal ? completablefuture / excutor service car appleé 100 000 x
	/**
	 *  Méthode permettant de récupérer la dernière position du user
	 *  Si aucune position n'est enregistrée alors trackUserLocation() est déclenché
	 *
	 * @param user
	 * @return visitedLocation
	 * @throws LocationNotFoundException si la localisation échoue
	 */
	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation;
		try {
			visitedLocation = (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation() : trackUserLocation(user).get();
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


	/**
	 * Méthode permettant de récupérer les offres de voyage d'un utilisateur
	 *
	 * @param user
	 * @return providers fournisseurs
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}


	/**
	 *  Méthode permettant de suivre la localisation d'un utilisateur de manière asynchrone
	 *  et d'appeler calculateRewards
	 *
	 * @param user
	 * @return CompletableFuture indiquant la position visitée
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user)   {

		//return CompletableFuture.supplyAsync(() -> {

			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			try {
				log.info("Avant le calcul des rewards pour '{}'", user.getUserName());
				return rewardsService.calculateRewards(user).thenApply(CompletableVoid -> visitedLocation); // arrête l'asynchrone - récupère donnée

			} catch (Exception e ){
				throw e;
			}

			//return visitedLocation;
		//}, ex);
    }




	/**
	 *  Méthode permettant de récupérer les 5 attractions les plus proches du user
	 *  et de calculer la distance et les pts de récompense pour chacune des attractions
	 *
	 * @param visitedLocation
	 * @param user
	 * @return nearAttractionList de 5 attractions
	 */
	public List<NearByAttractionDto> getNearByAttractions(VisitedLocation visitedLocation, User user) {

		// Localisation du user
		Location locationOfUser = visitedLocation.location;

		// On doit parcourir ttes les attractions avec stream - 26 in gpsUtil
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

					// retourner attractionName, latitude longitude user longitude latitude distance et les pts
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


	/**
	 * Hook d'arrêt de l'appli
	 * Il permet d'arrêter proprement tracker en cours d'exécution
	 *
	 */
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
