package com.example.admin.googlemapsproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    /*Map Variables*/
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    private GeoDataClient mGeoDataClient;
    private PlaceDetectionClient mPlaceDetectionClient;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Location mLastKnownLocation;

    private static final String TAG = MapsActivity.class.getSimpleName(); /*Get name of your class*/
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085); //Sydney
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    /*Socket Variables*/
    private Socket mSocket;
    private double vehicleLong,vehicleLat,oldVehicleLong,oldVehicleLat;
    private  LatLng vehiclePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Retrieve location and camera position from saved instance state*/
        if(savedInstanceState!=null){
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        /*Connect to Server and listen*/
        Connect2Server();
        mSocket.on("server-send-vehicle-position",onReceivePosition);

        setContentView(R.layout.activity_maps);

        // Construct a GeoDataClient.
        mGeoDataClient = Places.getGeoDataClient(this, null);

        // Construct a PlaceDetectionClient.
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        /*Build the map*/
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /*SocketIO function*/
    private void Connect2Server() {
        try {
            mSocket = IO.socket("https://vehicle-allocation-nano-pi.herokuapp.com/");
            mSocket.emit("android-client-connected","hi");
            mSocket.connect();

            oldVehicleLat = mDefaultLocation.latitude;
            oldVehicleLong = mDefaultLocation.longitude;

        } catch (URISyntaxException e) {
            Toast.makeText(this, "Server Refused to Connect!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private Emitter.Listener onReceivePosition = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject object = (JSONObject)args[0];
                    try {
                        if(object != null){
                            vehicleLong = object.getDouble("Longitude");
                            vehicleLat  = object.getDouble("Latitude");
                            System.out.printf("Longitude: %f",vehicleLong);
                            System.out.printf("latitude: %f",vehicleLat);
                        }else{
                            vehicleLong = mDefaultLocation.longitude;
                            vehicleLat = mDefaultLocation.latitude;
                        }
                    } catch (JSONException e) {
                        Toast.makeText(MapsActivity.this, "Error in Emitter Listener!", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }

                    if(object!=null){
                        if((oldVehicleLong!=vehicleLong) || (oldVehicleLat!=vehicleLat)){
                            /*If something changed*/
                            mMap.clear();
                            vehiclePosition = new LatLng(vehicleLat,vehicleLong);
                            mMap.addMarker(new MarkerOptions().position(vehiclePosition).title("Vehicle"));
                        }
                    }else{
                        vehiclePosition = mDefaultLocation;
                        //mMap.addMarker(new MarkerOptions().position(vehiclePosition).title("Default Location"));
                    }
                }
            });
        }
    };

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        /*Handle multiple lines of text in window contents*/
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,(FrameLayout) findViewById(R.id.map),false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        getLocationPermission();
        updateLocationUI();
        getDeviceLocation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(mMap!=null){
            outState.putParcelable(KEY_CAMERA_POSITION,mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION,mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode){
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:{
                //if request is cancelled, the result arrays are empty
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /*Current Place*/
    private void getDeviceLocation() {
        /**
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try{
            if(mLocationPermissionGranted){
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if(task.isSuccessful()){
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()),DEFAULT_ZOOM));
                        }else{
                            Log.d(TAG,"current location is null. Using defaults");
                            Log.e(TAG, "Exception: %s",task.getException());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation,DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }}catch(SecurityException e){
            e.printStackTrace();
        }
    }

    private void getLocationPermission(){
        /**
         * Get permission to access to current position
         * Used in updateLocationUI*/
        if(ContextCompat.checkSelfPermission(this.getApplicationContext(),android.Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){
            mLocationPermissionGranted = true;
        } else{
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void updateLocationUI() {
        /**
         * method to set the location controls on the map. If the user has granted location permission,
         * enable the My Location layer and the related control on the map,
         * otherwise disable the layer and the control, and set the current location to null*/

        if(mMap == null){
            return;
        }
        try{
            if(mLocationPermissionGranted){
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            }else{
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        }catch (SecurityException e){
            e.printStackTrace();
        }
    }
}
