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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;

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
import com.mapzen.tangram.TouchInput.RotateResponder;
import com.mapzen.tangram.TouchInput.ShoveResponder;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.apache.commons.text.WordUtils;

public class MapActivity extends Activity implements SceneLoadListener, TapResponder, DoubleTapResponder, FeaturePickListener, PanResponder, RotateResponder, ShoveResponder {

    private static final String TAG = "Underfoot::MapActivity";

    private static final String SCENE_FILE_PATH = "asset:///omt-scene.yml";

    private static final String[] FILES = {
        // From https://github.com/kueda/underfoot
        "underfoot_units-20190826.mbtiles",
        "underfoot_ways-20190912.mbtiles",
        "elevation-20190408.mbtiles"
    };
    private static final ArrayList<Number> FILE_DOWNLOAD_IDS = new ArrayList<Number>();
    public static final int REQUEST_ACCESS_FINE_LOCATION_CODE = 1;
    private ProgressBar mProgressBar;
    private Button mRetryDownloadButton;
    private TextView mDownloadProgressLabel;
    ViewGroup mDownloadUI;
    MapView mMapView;
    MapController mMapController;
    TextView mCrossHairs;
    TextView mSlideUpTitle;
    TextView mSlideUpLithology;
    TextView mSlideUpAge;
    TextView mDescription;
    TextView mEstAge;
    TextView mSource;
    TextView mMapMetadataLat;
    TextView mMapMetadataLon;
    TextView mMapMetadataZoom;
    double mLng;
    double mLat;
    float mZoom;
    FloatingActionButton mUserLocationButton;
    LocationManager mLocationManager;
    LocationListener mLocationListener;
    Location mUserLocation;
    Marker mCurrentLocationMarker = null;
    boolean mRequestingLocationUpdates = false;
    boolean mTrackingUserLocation = false;
    SlidingUpPanelLayout mSlidingPanel;

    //
    // Lifecycle
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Log.d(TAG, "onCreate");
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
        mCrossHairs = (TextView) findViewById(R.id.crossHairs);
        mSlideUpTitle = (TextView) findViewById(R.id.slideUpTitle);
        mSlideUpLithology = (TextView) findViewById(R.id.slideUpLithology);
        mSlideUpAge = (TextView) findViewById(R.id.slideUpAge);
        mDescription = (TextView) findViewById(R.id.description);
        mEstAge = (TextView) findViewById(R.id.estAge);
        mSource = (TextView) findViewById(R.id.source);
        mMapMetadataLat = (TextView) findViewById(R.id.mapMetadataLat);
        mMapMetadataLon = (TextView) findViewById(R.id.mapMetadataLon);
        mMapMetadataZoom = (TextView) findViewById(R.id.mapMetadataZoom);
        mZoom = 10;
        mLng = -122.2583;
        mLat = 37.8012;
        mUserLocationButton = (FloatingActionButton) findViewById(R.id.userLocationButton);
        mUserLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                panToCurrentLocation();
                if (mTrackingUserLocation) {
                    stopTrackingUserLocation();
                } else {
                    startTrackingUserLocation();
                }
            }
        });
        mSlidingPanel = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        mSlidingPanel.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                // Nothing to see here
            }

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
                if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    mSlideUpTitle.setMaxLines(10);
                    mSlideUpTitle.setEllipsize(null);
                } else {
                    mSlideUpTitle.setMaxLines(1);
                    mSlideUpTitle.setEllipsize(TextUtils.TruncateAt.END);
                }
            }
        });
        if (checkForRequiredFiles()) {
            hideDownloadUI();
        } else {
            showDownloadUI();
        }
        startGettingLocation();
    }

    @Override
    protected void onPause() {
        // Log.d(TAG, "onPause");
        super.onPause();
        stopGettingLocation();
        mMapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putFloat("mZoom", mZoom);
        outState.putDouble("mLng", mLng);
        outState.putDouble("mLat", mLat);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        // Log.d(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(inState);
        mZoom = inState.getFloat("mZoom");
        mLng = inState.getDouble("mLng");
        mLat= inState.getDouble("mLat");
    }

    @Override
    protected void onResume() {
        // Log.d(TAG, "onResume");
        super.onResume();
        mMapView.onResume();
        pickCenterFeature();
        startGettingLocation();
    }

    @Override
    protected void onStop() {
        // Log.d(TAG, "onStop");
        super.onStop();
        stopGettingLocation();
    }

    @Override
    public void onDestroy() {
        // Log.d(TAG, "onDestroy");
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        // Log.d(TAG, "onLowMemory");
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    //
    // Underfoot
    //

    private void showDownloadUI() {
        mMapView.setVisibility(View.GONE);
        mDownloadUI.setVisibility(View.VISIBLE);
        mUserLocationButton.setVisibility(View.GONE);
        mCrossHairs.setVisibility(View.GONE);
        mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    private void hideDownloadUI() {
        mMapView.setVisibility(View.VISIBLE);
        mDownloadUI.setVisibility(View.GONE);
        mUserLocationButton.setVisibility(View.VISIBLE);
        mCrossHairs.setVisibility(View.VISIBLE);
        mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
    }

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
        if (filesToDownload.size() > 0) {
            askToDownloadFiles();
            hideRetryDownloadButton();
            return false;
        } else {
            // Log.d(TAG, "All files loaded");
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
            Uri uri = Uri.parse("https://static.kueda.net/underfoot/" + fileName);
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
                            if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) >= DownloadManager.STATUS_SUCCESSFUL) {
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
                                    hideDownloadUI();
                                    mMapController.loadSceneFile(SCENE_FILE_PATH);
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
        mDownloadProgressLabel.setVisibility(View.INVISIBLE);
    }

    private void hideRetryDownloadButton() {
        mRetryDownloadButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mDownloadProgressLabel.setVisibility(View.VISIBLE);
    }

    private void pickCenterFeature() {
        PointF center = mMapController.lngLatToScreenPosition(mMapController.getPosition());
        mMapController.pickFeature(center.x, center.y);
    }

    private void startGettingLocation() {
        // Log.d(TAG, "starGettingLocation");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isBetterLocation(location, mUserLocation) && location.getAccuracy() < 100) {
                    mUserLocation = location;
                    showCurrentLocation();
                    if (mTrackingUserLocation) {
                        panToCurrentLocation();
                    }
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
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION_CODE);
            return;
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, mLocationListener);
        mRequestingLocationUpdates = true;
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
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
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
        // Log.d(TAG, "stopGettingLocation");
        mRequestingLocationUpdates = false;
        if (mLocationManager == null || mLocationListener == null) {
            return;
        }
        mLocationManager.removeUpdates(mLocationListener);
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
    }

    public void hideCurrentLocation() {
        if (mCurrentLocationMarker == null) {
            return;
        }
        mCurrentLocationMarker.setVisible(false);
    }

    public void panToCurrentLocation() {
        if (mUserLocation == null) {
            return;
        }
        mMapController.setPositionEased(new LngLat(mUserLocation.getLongitude(), mUserLocation.getLatitude()), 500);
        if (mZoom < 10) {
            mMapController.setZoomEased(10, 1000);
        }
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        handleMapMove();
                    }
                },
                1000);
    }

    private void startTrackingUserLocation() {
        mTrackingUserLocation = true;
        mUserLocationButton.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent));
    }

    private void stopTrackingUserLocation() {
        mTrackingUserLocation = false;
        mUserLocationButton.setColorFilter(ContextCompat.getColor(this, R.color.black));
    }

    private void handleMapMove() {
        LngLat centerCoordinates = mMapController.getPosition();
        mLng = centerCoordinates.longitude;
        mLat = centerCoordinates.latitude;
        mZoom = mMapController.getZoom();
        mMapMetadataLat.setText(String.format("%.2f", mLat));
        mMapMetadataLon.setText(String.format("%.2f", mLng));
        mMapMetadataZoom.setText(String.format("%.2f", mZoom));
        PointF center = mMapController.lngLatToScreenPosition(centerCoordinates);
        mMapController.pickFeature(center.x, center.y);
    }

    @Override
    public void onBackPressed() {
        if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    public String humanizeAge(String age) {
        if (age == null || age.length() == 0) {
            age = "?";
        } else {
            try {
                Float ageNum = Float.parseFloat(age);
                if (ageNum >= 1000000000) {
                    age = String.format("%.1f", ageNum / 1000000000.0 ) + " Ga";
                } else if (ageNum >= 1000000) {
                    age = String.format("%.1f", ageNum / 1000000.0 ) + " Ma";
                } else if (ageNum >= 100000) {
                    age = String.format("%.1f", ageNum / 1000.0 ) + " ka";
                } else {
                    age = String.format("%,d", Math.round(ageNum)) + " years";
                }
            } catch (NumberFormatException e) {
                // Just leave age alone
            }
        }
        return age;
    }

    //
    // Mapzen
    //

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        // Log.d(TAG, "onPostCreate");
        super.onPostCreate(savedInstanceState);
        mMapController = mMapView.getMap(this);
        mMapController.loadSceneFile(SCENE_FILE_PATH);
        // url: 'http://10.0.2.2:8080/data/v3/{z}/{x}/{y}.pbf'
        // This works but you actually don't need to do this if you know where the mbtiles file *will* be put in onCreate
//        Log.d("Underfoot", "trying to load mbtiles from " + mbtilesPath);
//        map.setMBTiles("osm", mbtilesPath);
        mMapController.setTapResponder(this);
        mMapController.setDoubleTapResponder(this);
        mMapController.setFeaturePickListener(this);
        mMapController.setPanResponder(this);
        mMapController.setRotateResponder(this);
        mMapController.setShoveResponder(this);
    }

    @Override
    public void onSceneReady(int sceneId, SceneError sceneError) {
        // Log.d(TAG, "onSceneReady");
        if (sceneError == null) {
//            Toast.makeText(this, "Scene ready: " + sceneId, Toast.LENGTH_SHORT).show();
            pickCenterFeature();
            mMapController.setPosition(new LngLat(mLng, mLat));
//            Toast.makeText(this, "setting zoom to " + mZoom, Toast.LENGTH_SHORT).show();
            mMapController.setZoom(mZoom);
        } else {
            Toast.makeText(this, "Scene load error: " + sceneId + " "
                    + sceneError.getSceneUpdate().toString()
                    + " " + sceneError.getError().toString(), Toast.LENGTH_SHORT).show();

            Log.d("Tangram", "Scene update errors "
                    + sceneError.getSceneUpdate().toString()
                    + " " + sceneError.getError().toString());
        }
        // Get the unit at the current location in a second, maybe b/c the local map data isn't loaded when onSceneReady fires
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                MapActivity.this.handleMapMove();
            }
        }, 1000);
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
            mSlideUpLithology.setText("Lithology: Unknown");
            mSlideUpAge.setText("Age: Unknown");
            mDescription.setText("Unknown");
        }
        // mSlideUpTitle.setText(mZoom + " - " + properties.get("title"));
        mSlideUpTitle.setText(properties.get("title"));
        String lithology = properties.get("lithology");
        if (lithology == null || lithology.length() == 0) {
            lithology = "Unknown";
        }
        mSlideUpLithology.setText("Lithology: " + lithology);
        String description = properties.get("description");
        if (description == null || description.length() == 0) {
            description = "Unknown";
        }
        mDescription.setText(description);
        String span = properties.get("span");
        if (span == null || span.length() == 0) {
            span = "Unknown";
        } else {
            span = WordUtils.capitalize(span).replace(" To ", " to ");
        }
        String estAge = this.humanizeAge(properties.get("est_age"));
        String age = span + " (" + estAge + ")";
        mSlideUpAge.setText("Age: " + age);
        mEstAge.setText(
            span +
            " (" +
            this.humanizeAge(properties.get("max_age")) +
            " - " +
            this.humanizeAge(properties.get("min_age")) +
            ")"
        );
        mSource.setText(properties.get("source"));
    }

    @Override
    public boolean onPan(float startX, float startY, float endX, float endY) {
        handleMapMove();
        // Log.d(TAG, "onPan");
        stopTrackingUserLocation();
        return false;
    }

    @Override
    public boolean onFling(float posX, float posY, float velocityX, float velocityY) {
        // Log.d(TAG, "onFling");
        return false;
    }

    @Override
    public boolean onRotate(float x, float y, float rotation) {
        // Disable rotation
        return true;
    }

    @Override
    public boolean onShove(float distance) {
        // Disable perspective changes
        return true;
    }
}
