package com.liskovsoft.appupdatechecker2.core;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.URLUtil;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManager;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManager.MyRequest;
import com.liskovsoft.sharedutils.helpers.FileHelpers;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Usage:
 * <pre>
 *   downloader = new AppDownloader(ctx, listener);
 *   downloader.download(new String[]{"http://serverurl/appfile.apk"});
 * </pre>
 */
public class AppDownloader {
    private static final String TAG = AppDownloader.class.getSimpleName();
    private static final String CURRENT_APK = "update.apk";
    // NEWTUBE(update-stall): process-wide - every instance writes the same cache file, and without the
    // old 60 s cap a download can outlive the presenter that started it (exit + manual re-check).
    private static final AtomicBoolean sInProgress = new AtomicBoolean();
    private final Context mContext;
    private final AppDownloaderListener mListener;
    private final int mMinApkSizeBytes;
    private AppDownloadTask mDownloadTask;
    private volatile boolean mAllowMetered = true;
    private volatile MyRequest mActiveRequest;

    public AppDownloader(Context context, AppDownloaderListener listener, int minApkSizeBytes) {
        mContext = context;
        mListener = listener;
        mMinApkSizeBytes = minApkSizeBytes;
    }

    /**
     * Uses first available url in the list.
     */
    public void download(Uri[] downloadUris) {
        download(downloadUris, true);
    }

    /**
     * Uses first available url in the list.
     * @param allowMetered NEWTUBE(update-metered): false for a download the user didn't ask for - it
     *                     won't start, and stops, while the active network is metered
     */
    public void download(Uri[] downloadUris, boolean allowMetered) {
        if (mDownloadTask != null) {
            // Ours is still running and reports to the same listener
            if (allowMetered) {
                upgradeToUserInitiated();
            }
            Log.e(TAG, "Another downloading in progress. Canceling...");
            return;
        }

        if (!sInProgress.compareAndSet(false, true)) {
            // Another instance is writing the same file. Answer, so a user-initiated check doesn't hang.
            Log.e(TAG, "Another downloading in progress (other instance). Canceling...");
            mListener.onDownloadError(new IllegalStateException("Another update download is in progress"));
            return;
        }

        mAllowMetered = allowMetered;
        mDownloadTask = new AppDownloadTask();
        // NEWTUBE(update-stall): off the SERIAL AsyncTask executor - a slow download may now take
        // minutes, and the version check's own AsyncTask would queue behind it on the serial one.
        mDownloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, downloadUris);
    }

    private void upgradeToUserInitiated() {
        mAllowMetered = true;
        MyRequest request = mActiveRequest;
        if (request != null) {
            request.setAllowMetered(true);
        }
    }

    private class AppDownloadTask extends AsyncTask<Uri[],Void,String> {
        @Override
        protected String doInBackground(Uri[]... args) {
            Uri[] uris = args[0];

            String path = null;
            for (Uri uri : uris) {
                if (URLUtil.isValidUrl(uri.toString())) {
                    path = downloadPackage(uri.toString());
                    if (path != null)
                        break;
                }
            }

            return path;
        }

        @Override
        protected void onPostExecute(String path) {
            if (path != null) {
                mListener.onApkDownloaded(path);
            } else {
                String msg = "Error while download. Install path is null";
                Log.e(TAG, msg);
                mListener.onDownloadError(new IllegalStateException(msg));
            }

            mActiveRequest = null;
            mDownloadTask = null;
            sInProgress.set(false);
        }

        private String downloadPackage(String uri) {
            File cacheDir = FileHelpers.getCacheDir(mContext);
            if (cacheDir == null) {
                return null;
            }
            File outputFile = new File(cacheDir, CURRENT_APK);
            String path = null;
            try {
                DownloadManager manager = new DownloadManager(mContext);
                MyRequest request = new MyRequest(Uri.parse(uri));
                request.setDestinationUri(Uri.fromFile(outputFile));
                request.setAllowMetered(mAllowMetered);
                mActiveRequest = request;
                try {
                    long id = manager.enqueue(request);
                    int size = manager.getSizeForDownloadedFile(id);
                    Uri destination = manager.getUriForDownloadedFile(id);

                    if (destination != null) {
                        // It could be a web page instead of apk
                        if (size > mMinApkSizeBytes) {
                            path = destination.getPath();
                        } else { // do cleanup
                            FileHelpers.delete(destination.getPath());
                        }
                    }
                } catch (IllegalStateException ex) { // 403 or something else
                    Log.d(TAG, ex.toString());
                    // NEWTUBE(update-stall): a transfer that died mid-file (read timeout, reset) left a
                    // partial APK behind; nothing can resume it, so don't keep it around.
                    FileHelpers.delete(outputFile.getPath());
                }
            } catch (IllegalStateException ex) { // CANNOT OBTAIN WRITE PERMISSIONS
                Log.e(TAG, ex.getMessage(), ex);
            }
            return path;
        }
    }

    /**
     * Process-wide: true while any instance is writing the update file.
     */
    public boolean isInProgress() {
        return sInProgress.get();
    }
}
