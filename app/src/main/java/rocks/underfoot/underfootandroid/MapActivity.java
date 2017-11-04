package rocks.underfoot.underfootandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
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

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putFloat("mZoom", mZoom);
        outState.putDouble("mLng", mLng);
        outState.putDouble("mLat", mLat);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        mZoom = inState.getFloat("mZoom");
        mLng = inState.getDouble("mLng");
        mLat= inState.getDouble("mLat");
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
            PointF center = mMapController.lngLatToScreenPosition(mMapController.getPosition());
            Log.d(TAG, "center: " + center);
            mMapController.pickFeature(center.x, center.y);
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
