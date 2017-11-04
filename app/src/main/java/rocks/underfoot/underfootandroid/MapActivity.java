package rocks.underfoot.underfootandroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import com.mapzen.tangram.LngLat;
import com.mapzen.tangram.MapController;
import com.mapzen.tangram.MapController.SceneLoadListener;
import com.mapzen.tangram.MapController.FeaturePickListener;
import com.mapzen.tangram.MapView;
import com.mapzen.tangram.Marker;
import com.mapzen.tangram.SceneError;
import com.mapzen.tangram.TouchInput.TapResponder;
import com.mapzen.tangram.TouchInput.DoubleTapResponder;
import com.mapzen.tangram.TouchInput.PanResponder;

public class MapActivity extends Activity implements SceneLoadListener, TapResponder, DoubleTapResponder, FeaturePickListener, PanResponder {

    private static final String TAG = "MapActivity";

    private static final String[] FILES = {
            "underfoot.mbtiles",
            "2017-07-03_california_san-francisco-bay.mbtiles"
    };
    private static final ArrayList<Number> FILE_DOWNLOAD_IDS = new ArrayList<Number>();
    public static final int REQUEST_ACCESS_FINE_LOCATION_CODE = 1;
    private ProgressBar mProgressBar;
    private Button mRetryDownloadButton;
    private TextView mDownloadProgressLabel ;
    ViewGroup mDownloadUI;
    MapView mMapView;
    MapController mMapController;
    TextView mSlideUpTitle;
    TextView mSlideUpBody;
    double mLng;
    double mLat;
    float mZoom;
    FloatingActionButton mUserLocationButton;
    LocationManager mLocationManager;
    LocationListener mLocationListener;
    Location mUserLocation;
    Marker mCurrentLocationMarker = null;
    boolean mRequestingLocationUpdates = false;

    //
    // Lifecycle
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mProgressBar = (ProgressBar) findViewById(R.id.downloadProgress);
        mRetryDownloadButton = (Button) findViewById(R.id.retryDownloadButton);
        mDownloadProgressLabel = (TextView) findViewById(R.id.downloadProgressLabel);
        mRetryDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkForRequiredFiles();
            }
        });
        mDownloadUI = (ViewGroup) findViewById(R.id.downloadUI);
        mMapView = (MapView) findViewById(R.id.map);
        mSlideUpTitle = (TextView) findViewById(R.id.slideUpTitle);
        mSlideUpBody = (TextView) findViewById(R.id.slideUpBody);
        if (checkForRequiredFiles()) {
            mMapView.setVisibility(View.VISIBLE);
            mDownloadUI.setVisibility(View.GONE);
        } else {
            mMapView.setVisibility(View.GONE);
            mDownloadUI.setVisibility(View.VISIBLE);
        }
        mZoom = 10;
        mLng = -122.2583;
        mLat = 37.8012;
        mUserLocationButton = (FloatingActionButton) findViewById(R.id.userLocationButton);
        mUserLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "clicked button, mLocationManager: " + mLocationManager);
                if (mRequestingLocationUpdates) {
                    stopGettingLocation();
                } else {
                    startGettingLocation();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGettingLocation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat("mZoom", mZoom);
        outState.putDouble("mLng", mLng);
        outState.putDouble("mLat", mLat);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        mZoom = inState.getFloat("mZoom");
        mLng = inState.getDouble("mLng");
        mLat= inState.getDouble("mLat");
    }

    protected void onResume() {
        super.onResume();
        pickCenterFeature();
    }

    //
    // Underfoot
    //

    private boolean checkForRequiredFiles() {
        ArrayList<String> filesToDownload = new ArrayList<String>();
        File file;
        for (String fileName: FILES) {
            file = new File(getExternalFilesDir("underfoot"), fileName);
            if (file.exists()) {
                Log.d(TAG, "Required file exists at " + file.getAbsolutePath());
            } else {
                filesToDownload.add(fileName);
            }
        }
        Log.d(TAG, "filesToDownload.size(): " + filesToDownload.size());
        if (filesToDownload.size() > 0) {
            askToDownloadFiles();
            hideRetryDownloadButton();
            return false;
        } else {
            Log.d(TAG, "All files loaded");
            return true;
        }
    }

    private void askToDownloadFiles() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Underfoot requires a few large files to operate offline. These can take a few minutes to download so you might want to be on WiFi.")
                .setTitle("Ready to download?")
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        downloadRequiredFiles();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showRetryDownloadButton();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void downloadRequiredFiles() {
        hideRetryDownloadButton();
        Toast.makeText(this, "Downloading", Toast.LENGTH_SHORT).show();
        final ArrayList<String> filesToDownload = new ArrayList<String>();
        File file;
        for (String fileName: FILES) {
            file = new File(getFilesDir(), fileName);
            if (file.exists()) {
                Log.d(TAG, "Required file exists at " + file.getAbsolutePath());
            } else {
                filesToDownload.add(fileName);
            }
        }
        final DownloadManager downloadManager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        for (String fileName: filesToDownload) {
            Uri uri = Uri.parse("http://static.kueda.net/underfoot/" + fileName);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setDestinationInExternalFilesDir(this, "underfoot", fileName);
            FILE_DOWNLOAD_IDS.add(downloadManager.enqueue(request));
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloading = true;
                while (downloading) {
                    int bytesDownloaded = 0;
                    int bytesTotal = 0;
                    ArrayList<Boolean> finishes = new ArrayList<Boolean>();
                    try {
                        for (Number fileDownloadID: FILE_DOWNLOAD_IDS) {
                            DownloadManager.Query q = new DownloadManager.Query();
                            q.setFilterById((long) fileDownloadID);
                            Cursor cursor = downloadManager.query(q);
                            cursor.moveToFirst();
                            bytesDownloaded = bytesDownloaded + cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                            bytesTotal = bytesTotal + cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                            if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                                finishes.add(true);
                            }
                            cursor.close();
                        }
                        final int downloadProgress = (int) ((bytesDownloaded * 100l)/ bytesTotal);
                        MapActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String msg = "Downloaded " + String.valueOf(downloadProgress) + "%";
                                mDownloadProgressLabel.setText(msg);
                                mProgressBar.setProgress(downloadProgress);
                            }
                        });
                        if (finishes.size() == filesToDownload.size()) {
                            downloading = false;
                            MapActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    FILE_DOWNLOAD_IDS.clear();
                                    mMapView.setVisibility(View.VISIBLE);
                                    mDownloadUI.setVisibility(View.GONE);
                                }
                            } );
                        }
                    } catch (CursorIndexOutOfBoundsException e) {
                        downloading = false;
                        FILE_DOWNLOAD_IDS.clear();
                        MapActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showRetryDownloadButton();
                            }
                        });
                    }
                }
           }
        }).start();
    }

    private void showRetryDownloadButton() {
        mRetryDownloadButton.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        mDownloadProgressLabel.setVisibility(View.GONE);
    }

    private void hideRetryDownloadButton() {
        mRetryDownloadButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mDownloadProgressLabel.setVisibility(View.VISIBLE);
    }

    private void pickCenterFeature() {
        PointF center = mMapController.lngLatToScreenPosition(mMapController.getPosition());
        Log.d(TAG, "center: " + center);
        mMapController.pickFeature(center.x, center.y);
    }

    private void startGettingLocation() {
        Log.d(TAG, "starGettingLocation");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isBetterLocation(location, mUserLocation) && location.getAccuracy() < 100) {
                    if (mUserLocation == null) {
                        mMapController.setPositionEased(new LngLat(location.getLongitude(), location.getLatitude()), 500);
                        mMapController.setZoomEased(12, 1000);
                    }
                    mUserLocation = location;
                    showCurrentLocation();
                } else {
                    Toast.makeText(getApplicationContext(), "Stopping location updates", Toast.LENGTH_LONG);
                    mLocationManager.removeUpdates(this);
                }
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "requesting fine location");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION_CODE);
            return;
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        mRequestingLocationUpdates = true;
//            mUserLocationButton.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent));
        mUserLocationButton.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent));
    }

    private static final int TWO_MINUTES = 1000 * 60 * 2;

    // Adapted from https://stackoverflow.com/questions/6181704/good-way-of-getting-the-users-location-in-android
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
//            Log.d(TAG, "location newer");
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
//            Log.d(TAG, "location older");
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
//            Log.d(TAG, "location more accurate");
            return true;
        } else if (isNewer && !isLessAccurate) {
//            Log.d(TAG, "location newer and not less accurate");
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
//            Log.d(TAG, "location newer and isn't crazy less accurate and from the same provider");
            return true;
        }
//        Log.d(TAG, "location not better");
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public void stopGettingLocation() {
        Log.d(TAG, "stopGettingLocation");
        mRequestingLocationUpdates = false;
        if (mLocationManager == null) {
            return;
        }
        mLocationManager.removeUpdates(mLocationListener);
        hideCurrentLocation();
        mUserLocationButton.setColorFilter(ContextCompat.getColor(this, R.color.black));
//        ActionMenuItemView menuItem = (ActionMenuItemView) findViewById(R.id.action_current_location);
//        menuItem.setIcon(R.drawable.ic_gps_off_black_24dp);
    }

    public void showCurrentLocation() {
        if (mUserLocation == null) {
            return;
        }
        if (mCurrentLocationMarker == null) {
            mCurrentLocationMarker = mMapController.addMarker();
        }
        mCurrentLocationMarker.setVisible(true);
        mCurrentLocationMarker.setPoint(new LngLat(mUserLocation.getLongitude(), mUserLocation.getLatitude()));
        mCurrentLocationMarker.setStylingFromString("{ style: 'points', color: [1, 0.25, 0.5, 0.5], size: [10px, 10px], order: 2000, collide: false }");
        Log.d(TAG, "currentLocationMarker: " + mCurrentLocationMarker);
    }

    public void hideCurrentLocation() {
        if (mCurrentLocationMarker == null) {
            return;
        }
        mCurrentLocationMarker.setVisible(false);
    }

    //
    // Mapzen
    //

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mMapController = mMapView.getMap(this);
        mMapController.loadSceneFile("asset:///omt-scene.yml");
        // url: 'http://10.0.2.2:8080/data/v3/{z}/{x}/{y}.pbf'
        // This works but you actually don't need to do this if you know where the mbtiles file *will* be put in onCreate
//        Log.d("Underfoot", "trying to load mbtiles from " + mbtilesPath);
//        map.setMBTiles("osm", mbtilesPath);
        mMapController.setPosition(new LngLat(mLng, mLat));
        mMapController.setZoom(mZoom);
        mMapController.setTapResponder(this);
        mMapController.setDoubleTapResponder(this);
        mMapController.setFeaturePickListener(this);
        mMapController.setPanResponder(this);
    }

    @Override
    public void onSceneReady(int sceneId, SceneError sceneError) {
        Log.d("Tangram", "onSceneReady!");
        if (sceneError == null) {
            Toast.makeText(this, "Scene ready: " + sceneId, Toast.LENGTH_SHORT).show();
            pickCenterFeature();
        } else {
            Toast.makeText(this, "Scene load error: " + sceneId + " "
                    + sceneError.getSceneUpdate().toString()
                    + " " + sceneError.getError().toString(), Toast.LENGTH_SHORT).show();

            Log.d("Tangram", "Scene update errors "
                    + sceneError.getSceneUpdate().toString()
                    + " " + sceneError.getError().toString());
        }
    }

    @Override
    public boolean onSingleTapUp(float v, float v1) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(float x, float y) {
        LngLat tappedPoint = mMapController.screenPositionToLngLat(new PointF(x, y));
        mMapController.pickFeature(x,y);
        mMapController.setPositionEased(tappedPoint, 1000);
        return true;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        mMapController.setZoomEased(mMapController.getZoom() + 1.f, 500);
        LngLat tapped = mMapController.screenPositionToLngLat(new PointF(x, y));
        LngLat current = mMapController.getPosition();
        LngLat next = new LngLat(
                0.5 * (tapped.longitude + current.longitude),
                0.5 * (tapped.latitude + current.latitude)
        );
        mMapController.setPositionEased(next, 500);
        return true;
    }

    @Override
    public void onFeaturePick(java.util.Map<String,String> properties,
                              float positionX,
                              float positionY) {
        if (properties.isEmpty()) {
            mSlideUpTitle.setText("Unknown");
            mSlideUpBody.setText("");
        }
        String title = properties.get("title");
        String description = properties.get("description");
        mSlideUpTitle.setText(title);
        mSlideUpBody.setText(description);
    }

    @Override
    public boolean onPan(float startX, float startY, float endX, float endY) {
        LngLat centerCoordinates = mMapController.getPosition();
        mLng = centerCoordinates.longitude;
        mLat = centerCoordinates.latitude;
        mZoom = mMapController.getZoom();
        PointF center = mMapController.lngLatToScreenPosition(centerCoordinates);
        mMapController.pickFeature(center.x, center.y);
        return false;
    }

    @Override
    public boolean onFling(float posX, float posY, float velocityX, float velocityY) {
        return false;
    }
}
