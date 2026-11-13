package com.aqarcom.app.ui.tender;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.ApiResponse;
import com.aqarcom.app.data.model.TenderDocument;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.ErrorParser;
import com.aqarcom.app.data.network.MultipartUtil;
import com.aqarcom.app.data.upload.ChunkedUploader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Spec-book files (FR-11 / FR-15):
 *  - download a version (decrypted by the server) and open it with an installed PDF / Word viewer
 *  - upload a new version: files above 5 MB go through the resumable chunked uploader (NFR-01)
 */
public final class DocumentFiles {
    public static final String[] MIME_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
    private static final long CHUNK_THRESHOLD = 5L * 1024 * 1024;
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DocumentFiles() {}

    public interface Done<T> {
        void onSuccess(T value);
        void onError(ApiError e);
    }

    public interface Progress { void onProgress(int percent); }

    /** Download a version (0 = latest) into the cache and open it. */
    public static void downloadAndOpen(Activity a, String tenderId, int version, String fileName, String mime, Done<File> done) {
        IO.execute(() -> {
            try {
                Response<ResponseBody> res = version > 0
                        ? ApiClient.tenders().downloadVersion(tenderId, version).execute()
                        : ApiClient.tenders().downloadLatest(tenderId).execute();
                if (!res.isSuccessful() || res.body() == null) {
                    ApiError e = ErrorParser.parse(res);
                    MAIN.post(() -> done.onError(e));
                    return;
                }
                File dir = new File(a.getCacheDir(), "downloads");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, safeName(fileName));
                try (InputStream in = res.body().byteStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
                MAIN.post(() -> {
                    done.onSuccess(out);
                    open(a, out, mime);
                });
            } catch (Exception ex) {
                MAIN.post(() -> done.onError(ErrorParser.fromThrowable(ex)));
            }
        });
    }

    private static void open(Activity a, File f, String mime) {
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "application/pdf" : mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, f.getName()));
        } catch (ActivityNotFoundException ignored) { }
    }

    private static String safeName(String n) {
        if (n == null || n.trim().isEmpty()) return "spec-book.pdf";
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** Upload a picked file as a new spec-book version. */
    public static void upload(Activity a, String tenderId, Uri uri, String note, Progress progress, Done<TenderDocument> done) {
        long size = MultipartUtil.size(a, uri);
        if (size > MAX_BYTES) {
            ApiError e = new ApiError();
            e.code = "FILE_TOO_LARGE";
            e.message = "حجم الملف يتجاوز الحد الأقصى المسموح (20 ميغابايت). الصيغ المقبولة: PDF, Word";
            done.onError(e);
            return;
        }
        if (size > CHUNK_THRESHOLD) {
            new ChunkedUploader(a).upload(tenderId, uri, note, new ChunkedUploader.Listener() {
                @Override public void onProgress(int percent) { progress.onProgress(percent); }
                @Override public void onDone(TenderDocument d) { done.onSuccess(d); }
                @Override public void onError(ApiError e) { done.onError(e); }
            });
            return;
        }
        IO.execute(() -> {
            try {
                MultipartBody.Part part = MultipartUtil.part(a, "file", uri, (written, total) -> {
                    if (total > 0) {
                        int pct = (int) (written * 100 / total);
                        MAIN.post(() -> progress.onProgress(pct));
                    }
                });
                RequestBody noteBody = note == null || note.isEmpty() ? null : MultipartUtil.text(note);
                Response<ApiResponse<TenderDocument>> res = ApiClient.tenders().uploadDocument(tenderId, part, noteBody).execute();
                if (res.isSuccessful() && res.body() != null) {
                    TenderDocument d = res.body().data;
                    MAIN.post(() -> done.onSuccess(d));
                } else {
                    ApiError e = ErrorParser.parse(res);
                    MAIN.post(() -> done.onError(e));
                }
            } catch (Exception ex) {
                MAIN.post(() -> done.onError(ErrorParser.fromThrowable(ex)));
            }
        });
    }
}
