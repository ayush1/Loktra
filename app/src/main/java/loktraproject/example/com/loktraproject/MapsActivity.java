package loktraproject.example.com.loktraproject;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import loktraproject.example.com.loktraproject.Utils.AppConstants;
import loktraproject.example.com.loktraproject.Utils.DirectionsJSONParser;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener,
        ResultCallback<LocationSettingsResult>, View.OnClickListener {

    GoogleMap map;
    GoogleApiClient googleApiClient;
    Location location;
    LocationSettingsRequest locationSettingsRequest;
    LocationRequest locationRequest;
    LatLng latLng;

    protected static final String TAG = "MapsActivity";

    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private int REQUEST_CHECK_SETTINGS = 1;
    private boolean requestingLocationUpdates;

    private float distance;

    Button btnShift;
    TextView tvShiftTime;
    CardView cardView;
    private double randomLatitude;
    private double randomLongitude;
    boolean isShiftStart;

    ArrayList<LatLng> markerPoints = new ArrayList();
    Location randomLocation;
    private String message;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

//        Calculating random radius using speed and time.
        distance = Float.parseFloat(AppConstants.speed) * Integer.parseInt(AppConstants.time);

        btnShift = (Button) findViewById(R.id.btn_shift);
        tvShiftTime = (TextView) findViewById(R.id.shift_time);
        cardView = (CardView) findViewById(R.id.card_view);
        int time = Integer.parseInt(AppConstants.time)/60;

        tvShiftTime.setText(time + "mins");
        btnShift.setOnClickListener(this);

        buildGoogleApiCient();
        createLocationRequest();
        buildLocationSettingsRequest();
    }

    protected void buildGoogleApiCient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                message = getString(R.string.permission_denied_message);
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setMessage(message)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                checkLocationSettings();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        });
                dialog.show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } else {
            if (location != null) {
                location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                updateLocationUI();
            }else{
                checkLocationSettings();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                checkLocationSettings();
        }
    }

    protected void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(googleApiClient, locationSettingsRequest);
        result.setResultCallback(this);
    }


    private void updateLocationUI() {
        if(location != null) {
            map.clear();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
            CameraUpdate zoom = CameraUpdateFactory.zoomTo(12);
            map.moveCamera(cameraUpdate);
            map.animateCamera(zoom);
            map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title(getString(R.string.source_location)));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (location != null) {
            map.clear();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
            CameraUpdate zoom = CameraUpdateFactory.zoomTo(12);
            map.moveCamera(cameraUpdate);
            map.animateCamera(zoom);
            map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title(getString(R.string.source_location)));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK && requestCode == REQUEST_CHECK_SETTINGS) {
            startLocationUpdates();
        }
    }

    @Override
    public void onResult(LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
         switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                try {
                    status.startResolutionForResult(MapsActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    Log.i(TAG, "Unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                Log.i(TAG, "Dialog not created.");
                break;
        }
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this)
                .setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                requestingLocationUpdates = true;
            }
        });

    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this)
                .setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                requestingLocationUpdates = false;
            }
        });
    }

    public Location getRandomLocation(LatLng point, float radius) {

        Random random = new Random();

        double x0 =(double) point.longitude;
        double y0 = (double)point.latitude;

        // Convert radius from meters to degrees
        double radiusInDegrees = radius / 111300;

        double u = random.nextDouble();
        double v = random.nextDouble();
        double w = radiusInDegrees * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        double x = w * Math.cos(t);
        double y = w * Math.sin(t);

        double new_x = x / Math.cos(y0);

        double foundLongitude = new_x + x0;
        double foundLatitude = y + y0;

        Location rl =  new Location("");
        rl.setLatitude(foundLatitude);
        rl.setLongitude(foundLongitude);
        return rl;
    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (googleApiClient.isConnected() && requestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_shift:
                if(!isShiftStart){
                    btnShift.setText("Stop Shift");
                    isShiftStart = true;
                    cardView.setVisibility(View.GONE);
                    if(latLng != null && randomLocation == null){
                        randomLocation = getRandomLocation(latLng, distance);
                        randomLatitude = randomLocation.getLatitude();
                        randomLongitude = randomLocation.getLongitude();

                        markerPoints.add(latLng);
                        markerPoints.add(new LatLng(randomLatitude, randomLongitude));
                    }
                }else{
                    btnShift.setText("Start Shift");
                    isShiftStart = false;
                    cardView.setVisibility(View.VISIBLE);
                    addMarkerToMap();
                    getRoutePath(markerPoints);
                }
        }
    }

    private void getRoutePath(ArrayList<LatLng> markerPoints) {
        MarkerOptions markerOptions = new MarkerOptions();

        markerOptions.position(latLng);
        String url = getDirectionsUrl(markerPoints.get(0), markerPoints.get(1));

        DownloadTask downloadTask = new DownloadTask();
        downloadTask.execute(url);
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String sensor = "sensor=false";
        String mode = "mode=driving";
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);

        }
    }


    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap>>> {
        @Override
        protected List<List<HashMap>> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<List<HashMap>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap>> result) {
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap point = path.get(j);

                    double lat = Double.parseDouble(String.valueOf(point.get("lat")));
                    double lng = Double.parseDouble(String.valueOf(point.get("lng")));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(Color.RED);
                lineOptions.geodesic(true);
            }
            map.addPolyline(lineOptions);
        }
    }


    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }


    private void addMarkerToMap() {
        CameraUpdate driverCameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(randomLatitude, randomLongitude));
        map.moveCamera(driverCameraUpdate);

        map.addMarker(new MarkerOptions().position(new LatLng(randomLatitude, randomLongitude)).title(getString(R.string.destination))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        CameraUpdate riderCameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(latLng.latitude, latLng.longitude));
        map.moveCamera(riderCameraUpdate);

        map.addMarker(new MarkerOptions().position(new LatLng(latLng.latitude, latLng.longitude)).title(getString(R.string.source_location))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        CameraUpdate zoom = CameraUpdateFactory.zoomTo(12);
        map.animateCamera(zoom);
    }
}
