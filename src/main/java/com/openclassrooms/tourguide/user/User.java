package com.openclassrooms.tourguide.user;

import java.util.*;

import gpsUtil.location.VisitedLocation;
import lombok.Getter;
import lombok.Setter;
import tripPricer.Provider;

@Setter
@Getter
public class User {
	private final UUID userId;
	private final String userName;

    private String phoneNumber;
    private String emailAddress;
    private Date latestLocationTimestamp;

	private List<VisitedLocation> visitedLocations = new ArrayList<>();

	///  Test 2 performance
	private List<UserReward> userRewards = new ArrayList<>();

    private UserPreferences userPreferences = new UserPreferences();

    private List<Provider> tripDeals = new ArrayList<>();

	public User(UUID userId, String userName, String phoneNumber, String emailAddress) {
		this.userId = userId;
		this.userName = userName;
		this.phoneNumber = phoneNumber;
		this.emailAddress = emailAddress;
	}

    public void addToVisitedLocations(VisitedLocation visitedLocation) {

		visitedLocations.add(visitedLocation);
	}

    public void clearVisitedLocations() {

		visitedLocations.clear();
	}
	
	public void addUserReward(UserReward userReward) {

		if(userRewards.isEmpty() || userRewards
				.stream()
				.filter(r -> r.attraction.attractionName.equals(userReward.attraction.attractionName)).count() == 0) {
			userRewards.add(userReward);
		}
	}

    public VisitedLocation getLastVisitedLocation() {

		return visitedLocations.get(visitedLocations.size() - 1);
	}

}
