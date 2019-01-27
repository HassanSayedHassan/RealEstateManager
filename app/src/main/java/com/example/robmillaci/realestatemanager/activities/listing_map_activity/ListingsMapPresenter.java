package com.example.robmillaci.realestatemanager.activities.listing_map_activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.example.robmillaci.realestatemanager.R;
import com.example.robmillaci.realestatemanager.data_objects.Listing;
import com.example.robmillaci.realestatemanager.databases.firebase.FirebaseHelper;
import com.example.robmillaci.realestatemanager.databases.local_database.MyDatabase;
import com.example.robmillaci.realestatemanager.json_location_objects.LocationObject;
import com.example.robmillaci.realestatemanager.utils.Utils;
import com.example.robmillaci.realestatemanager.web_services.GetDataService;
import com.example.robmillaci.realestatemanager.web_services.ServiceGenerator;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.facebook.FacebookSdk.getApplicationContext;

/**
 * This class is the link between {@link ListingsMapView} and both {@link FirebaseHelper} & {@link MyDatabase}
 * The listing are obtained from the databases, the users location is also returned to the view from this class as well as geo locating the listings
 */
public class ListingsMapPresenter implements FirebaseHelper.Model, MyDatabase.Model {
    private View view;

    ListingsMapPresenter(View v) {
        this.view = v;
    } //the view for this presenter


    /**
     * Get all the listings from either the local database or from Firebase
     * @param weakContext
     */
    void getAllListings(WeakReference<Context> weakContext) {
        Context c = weakContext.get();
        if (Utils.CheckConnectivity(c)) {
            FirebaseHelper.getInstance().setPresenter(this).getAllListings();
        } else {
            MyDatabase.getInstance(c).setPresenter(this).searchLocalDB(c, null, 1);
        }
    }


    @SuppressLint("MissingPermission")
    /**
     * Gets the users last known location and returns this to the view to display on the map
     */
    void getLastKnownLocation() {
        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // GPS location can be null if GPS is switched off
                        if (location != null) {
                            view.gotUsersLocation(location.getLatitude(), location.getLongitude());
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.error_getting_gps, Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                });
    }


    /**
     * Interface callback method from {@link FirebaseHelper#getAllListings()}
     * @param listings the returned listings
     */
    @Override
    public void gotListingsFromFirebase(ArrayList<Listing> listings) {
        view.gotAllListings(listings);
    }


    /**
     * Interface callback method from {@link MyDatabase#searchLocalDB(Context, Bundle, int)}
     * @param listings listings returned
     * @param requestCode any returned request code
     * @param c the context returned
     */
    @Override
    public void gotDataFromLocalDb(ArrayList<Listing> listings, int requestCode, Context c) {
        view.gotAllListings(listings);
    }


    /**
     * From an address string obtained from a listing, this method returns the listings latitude and longitude so we can show the listing location
     * on the map.
     * @param address the address of the listing
     * @param markerIndex the index of the marker representing the listing
     */
    void geoLocationListing(String address, final int markerIndex) {
        GetDataService service = ServiceGenerator.getRetrofitInstance().create(GetDataService.class);
        retrofit2.Call<LocationObject> call = service.getDetails(address, ServiceGenerator.getGoogleAPIKey());

        call.enqueue(new Callback<LocationObject>() {
            @Override
            public void onResponse(@NonNull Call<LocationObject> call, @NonNull Response<LocationObject> response) {
                LocationObject thisLocation = response.body();

                if (thisLocation != null && thisLocation.toString().toLowerCase().contains("limit")) {
                    Toast.makeText(getApplicationContext(), R.string.api_limit_reached, Toast.LENGTH_SHORT).show();
                }

                try {
                    //noinspection ConstantConditions
                    view.gotPlaceLatLng(thisLocation.getResults().get(0).getGeometry().getLocation().getLat(),
                            thisLocation.getResults().get(0).getGeometry().getLocation().getLng(), markerIndex);
                } catch (Exception e) {
                    view.gotPlaceLatLng(0, 0, 0);
                }
            }

            @Override
            public void onFailure(@NonNull Call<LocationObject> call, @NonNull Throwable t) {
                view.gotPlaceLatLng(0, 0, 0);
            }
        });
    }

    /**
     * the Views interface methods
     */
    interface View {
        void gotAllListings(ArrayList<Listing> listings);

        void gotUsersLocation(double latitude, double longitude);

        void gotPlaceLatLng(double latitude, double longitude, int markerIndex);
    }
}
