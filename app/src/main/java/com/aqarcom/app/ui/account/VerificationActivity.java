package com.aqarcom.app.ui.account;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.aqarcom.app.R;
import com.aqarcom.app.data.model.ApiError;
import com.aqarcom.app.data.model.ApiResponse;
import com.aqarcom.app.data.model.PageMeta;
import com.aqarcom.app.data.model.User;
import com.aqarcom.app.data.model.VerificationRequest;
import com.aqarcom.app.data.model.VerificationStatus;
import com.aqarcom.app.data.network.ApiCallback;
import com.aqarcom.app.data.network.ApiClient;
import com.aqarcom.app.data.network.ErrorParser;
import com.aqarcom.app.data.network.MultipartUtil;
import com.aqarcom.app.data.network.TokenManager;
import com.aqarcom.app.databinding.ActivityVerificationBinding;
import com.aqarcom.app.ui.base.BaseActivity;
import com.aqarcom.app.ui.common.Fmt;
import com.aqarcom.app.ui.common.Ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MultipartBody;
import retrofit2.Response;

/** FR-22 — contractor uploads the engineers' syndicate certificate and/or commercial register (PDF or image). */
public class VerificationActivity extends BaseActivity {
    private static final String[] TYPES = {"application/pdf", "image/jpeg", "image/png", "image/webp"};

    private ActivityVerificationBinding b;
    private Uri syndicate, register;
    private boolean pickingSyndicate;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        String name = MultipartUtil.displayName(this, uri) + "  ·  " + Fmt.number(MultipartUtil.size(this, uri) / 1048576.0) + " MB";
        if (pickingSyndicate) { syndicate = uri; b.tvSyndicate.setText(name); b.tvSyndicate.setTextColor(getColor(R.color.aq_emerald)); }
        else { register = uri; b.tvRegister.setText(name); b.tvRegister.setTextColor(getColor(R.color.aq_emerald)); }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVerificationBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar, null, true);
        b.rowSyndicate.setOnClickListener(v -> { pickingSyndicate = true; pick.launch(TYPES); });
        b.rowRegister.setOnClickListener(v -> { pickingSyndicate = false; pick.launch(TYPES); });
        b.btnSubmit.setOnClickListener(v -> submit());
        load();
    }

    private void load() {
        ApiClient.verification().me().enqueue(new ApiCallback<VerificationStatus>() {
            @Override public void onSuccess(VerificationStatus s, PageMeta meta) { bind(s); }
            @Override public void onError(ApiError e) { Ui.error(b.getRoot(), e); }
        });
    }

    private void bind(VerificationStatus s) {
        String st = s.verificationStatus;
        Ui.verificationPill(b.tvStatus, st);
        VerificationRequest last = s.latestRequest;
        String detail;
        switch (st == null ? "none" : st) {
            case "verified": detail = "حسابك موثّق ويمكنك تقديم العروض على المناقصات."; break;
            case "pending": detail = "وثائقك قيد المراجعة اليدوية من قبل الإدارة" + (last != null ? " (أُرسلت " + Fmt.dateTime(last.createdAt) + ")" : "") + "."; break;
            case "rejected": detail = "رُفض طلبك السابق. صحّح الوثائق وأعد الإرسال."; break;
            default: detail = "لم ترسل وثائق التوثيق بعد.";
        }
        b.tvStatusDetail.setText(detail);
        boolean rejected = "rejected".equals(st) && last != null && last.rejectionReason != null;
        b.tvRejection.setVisibility(rejected ? View.VISIBLE : View.GONE);
        if (rejected) b.tvRejection.setText("سبب الرفض: " + last.rejectionReason);
        boolean canSubmit = !"verified".equals(st) && !"pending".equals(st);
        b.groupUpload.setVisibility(canSubmit ? View.VISIBLE : View.GONE);
        b.barSubmit.setVisibility(canSubmit ? View.VISIBLE : View.GONE);

        // keep the cached user in sync for the rest of the app
        User u = TokenManager.get(this).getUser();
        if (u != null && st != null && !st.equals(u.verificationStatus)) {
            u.verificationStatus = st;
            TokenManager.get(this).saveUser(u);
        }
    }

    private void submit() {
        if (syndicate == null && register == null) {
            Ui.snack(b.getRoot(), "يجب إرفاق شهادة نقابة المهندسين أو السجل التجاري");
            return;
        }
        Ui.busy(b.btnSubmit, true);
        io.execute(() -> {
            try {
                MultipartBody.Part a = syndicate == null ? null : MultipartUtil.part(this, "engineers_syndicate", syndicate, null);
                MultipartBody.Part c = register == null ? null : MultipartUtil.part(this, "commercial_register", register, null);
                Response<ApiResponse<VerificationRequest>> res = ApiClient.verification().submit(a, c, null, null).execute();
                main.post(() -> {
                    Ui.busy(b.btnSubmit, false);
                    if (res.isSuccessful()) {
                        Ui.toast(this, getString(R.string.verification_submitted));
                        load();
                    } else Ui.error(b.getRoot(), ErrorParser.parse(res));
                });
            } catch (Exception ex) {
                main.post(() -> {
                    Ui.busy(b.btnSubmit, false);
                    Ui.error(b.getRoot(), ErrorParser.fromThrowable(ex));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
