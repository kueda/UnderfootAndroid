package rocks.underfoot.underfootandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class MapActivity extends Activity {

    private static final String TAG = "MapActivity";

    private static final String[] FILES = {
            "underfoot.mbtiles",
            "2017-07-03_california_san-francisco-bay.mbtiles"
    };
    private static final ArrayList<Number> FILE_DOWNLOAD_IDS = new ArrayList<Number>();
    private ProgressBar mProgressBar;
    private Button mRetryDownloadButton;
    private TextView mDownloadProgressLabel ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mProgressBar = (ProgressBar) findViewById(R.id.downloadProgress);
        mRetryDownloadButton = (Button) findViewById(R.id.retryDownloadButton);
        mDownloadProgressLabel = (TextView) findViewById(R.id.downloadProgressLabel);
        checkForRequiredFiles();
        mRetryDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkForRequiredFiles();
            }
        });
    }

    private void checkForRequiredFiles() {
        Log.d(TAG, "checkForRequiredFiles");
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
        } else {
            Log.d(TAG, "All files loaded");
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
                            FILE_DOWNLOAD_IDS.clear();
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
}
