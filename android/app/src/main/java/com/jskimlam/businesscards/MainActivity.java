package com.jskimlam.businesscards; // 앱 패키지

// ===================== 안드로이드 기본 =====================
import android.Manifest;                               // 권한 상수
import android.annotation.SuppressLint;                // 경고 억제
import android.app.Activity;                           // 기본 액티비티
import android.app.DownloadManager;                    // 파일 다운로드
import android.content.ActivityNotFoundException;      // 실행 앱 없음 예외
import android.content.ContentValues;                  // MediaStore 메타데이터
import android.content.Intent;                         // 인텐트
import android.content.IntentSender;                   // 스캐너 실행용 인텐트센더
import android.content.pm.PackageManager;              // 권한 결과 상수
import android.graphics.Bitmap;                        // 비트맵
import android.graphics.BitmapFactory;                 // 비트맵 디코딩
import android.graphics.Color;                         // 색상
import android.graphics.Rect;                          // 글자 영역 사각형
import android.net.Uri;                                // URI
import android.os.Build;                               // OS 버전
import android.os.Bundle;                              // 상태 번들
import android.os.Environment;                         // 공용 폴더 경로
import android.view.View;                              // 시스템바 아이콘 색
import android.provider.MediaStore;                    // 미디어 저장소
import android.util.Base64;                            // Base64 인코딩/디코딩
import android.webkit.CookieManager;                   // 쿠키 설정
import android.webkit.JavascriptInterface;             // JS 브리지 어노테이션
import android.webkit.PermissionRequest;               // 웹 카메라 권한 요청
import android.webkit.URLUtil;                         // 파일명 추정
import android.webkit.ValueCallback;                   // 파일 선택 콜백
import android.webkit.WebChromeClient;                 // 크롬 클라이언트
import android.webkit.WebResourceError;                // 로딩 오류
import android.webkit.WebResourceRequest;              // 요청 정보
import android.webkit.WebSettings;                     // 웹뷰 설정
import android.webkit.WebView;                         // 웹뷰
import android.webkit.WebViewClient;                   // 웹뷰 클라이언트
import android.widget.FrameLayout;                     // 루트 레이아웃
import android.widget.Toast;                           // 토스트 메시지

// ===================== Google ML Kit =====================
import com.google.android.gms.tasks.Tasks;                                      // Task 동기 대기
import com.google.mlkit.vision.common.InputImage;                               // OCR 입력 이미지
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;              // 문서 스캐너
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;       // 스캐너 옵션
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;             // 스캐너 팩토리
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;       // 스캔 결과
import com.google.mlkit.vision.text.Text;                                       // OCR 결과
import com.google.mlkit.vision.text.TextRecognition;                            // OCR 팩토리
import com.google.mlkit.vision.text.TextRecognizer;                             // OCR 엔진
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;         // 한글 OCR 옵션

// ===================== 자바 표준 =====================
import org.json.JSONArray;                             // JSON 배열
import org.json.JSONObject;                            // JSON 객체

import java.io.ByteArrayOutputStream;                  // 바이트 버퍼
import java.io.File;                                   // 캐시 파일
import java.io.FileOutputStream;                       // 파일 쓰기
import java.io.InputStream;                            // 입력 스트림
import java.io.OutputStream;                           // 출력 스트림
import java.nio.charset.StandardCharsets;              // UTF-8
import java.util.concurrent.ExecutorService;           // 백그라운드 스레드풀
import java.util.concurrent.Executors;                 // 스레드풀 생성

/**
 * 명함보관함 메인 화면
 *  - GitHub Pages 웹앱을 WebView로 띄움
 *  - "CardAndroid" JS 브리지로 네이티브 스캔 모드(구글 문서 스캐너) + 기기 내 한글 OCR 제공
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://jskimlam.github.io/business-cards/"; // 웹앱 주소
    private static final String APP_VERSION = "2.1.0";                                // 브리지 버전
    private static final int PAPER = Color.rgb(244, 241, 234);                        // 새 디자인 바탕색(종이)
    private static final int FILE_CHOOSER_REQUEST = 1001;                             // 파일 선택 요청코드
    private static final int SCAN_REQUEST = 1002;                                     // 스캐너 요청코드
    private static final int PERMISSION_REQUEST_CAMERA = 2001;                        // 카메라 권한 요청코드
    private static final int OCR_MAX_SIDE = 2200;                                     // OCR용 최대 변 길이(px)
    private static final int SAVE_MAX_WIDTH = 1400;                                   // 저장 이미지 최대 가로(px)

    private WebView webView;                                   // 웹뷰
    private ValueCallback<Uri[]> fileCallback;                 // 파일 선택 결과 콜백
    private final ExecutorService worker = Executors.newSingleThreadExecutor(); // 무거운 작업용 스레드
    private TextRecognizer recognizer;                         // 한글 OCR 엔진(지연 생성)
    private boolean scanInProgress = false;                    // 스캐너 중복 실행 방지
    private boolean scanForChooser = false;                    // 웹의 카메라 버튼에서 연 스캔인지

    /** 앱 내부 주소인지 판별 (외부 링크는 다른 앱으로 넘김) */
    private boolean isAppUrl(Uri uri) {
        if (uri == null) return false;                                     // 빈 주소 제외
        final String path = uri.getPath();                                 // 경로
        return "https".equalsIgnoreCase(uri.getScheme())                   // https 만 허용
                && "jskimlam.github.io".equalsIgnoreCase(uri.getHost())    // 호스트 일치
                && path != null
                && (path.equals("/business-cards") || path.startsWith("/business-cards/")); // 앱 경로
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);                                 // 부모 초기화
        applySystemBars(PAPER, true);                                       // 상단·하단바: 종이색 + 어두운 아이콘

        FrameLayout root = new FrameLayout(this);                           // 루트 레이아웃
        root.setBackgroundColor(PAPER);                                     // 배경색

        webView = new WebView(this);                                        // 웹뷰 생성
        webView.setBackgroundColor(PAPER);                                  // 로딩 중 깜빡임 방지
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)); // 전체 화면
        setContentView(root);                                               // 화면 지정

        final WebSettings settings = webView.getSettings();                 // 웹뷰 설정
        settings.setJavaScriptEnabled(true);                                // JS 허용
        settings.setDomStorageEnabled(true);                                // localStorage 허용(명함 저장소)
        settings.setDatabaseEnabled(true);                                  // DB 허용
        settings.setAllowFileAccess(true);                                  // 파일 접근
        settings.setAllowContentAccess(true);                               // content:// 접근
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); // 혼합콘텐츠 차단
        settings.setSupportZoom(false);                                     // 확대 비활성
        settings.setBuiltInZoomControls(false);                             // 확대 버튼 숨김
        settings.setMediaPlaybackRequiresUserGesture(false);                // 카메라 미리보기 자동재생
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);                    // 기본 캐시
        settings.setSafeBrowsingEnabled(true);                              // 세이프브라우징
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false); // 서드파티 쿠키 차단

        webView.addJavascriptInterface(new CardNativeBridge(), "CardAndroid"); // JS 브리지 등록

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA); // 카메라 권한 요청
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();                              // 이동할 주소
                if (isAppUrl(target)) return false;                         // 앱 내부면 웹뷰에서 열기
                handleExternalUri(target);                                  // 전화/메일/외부링크
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {                             // 본문 로딩 실패 시
                    Toast.makeText(MainActivity.this,
                            "네트워크 연결을 확인하세요. 오프라인 모드로 실행됩니다.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {                                        // 웹 카메라(getUserMedia) 허용
                    try {
                        request.grant(request.getResources());
                    } catch (Exception ignored) {}
                });
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view, ValueCallback<Uri[]> newCallback, FileChooserParams params) {
                if (fileCallback != null) {                                 // 이전 콜백 정리
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                }
                fileCallback = newCallback;                                 // 새 콜백 저장
                boolean wantsImage = false;                                 // 이미지 요청인지
                for (String t : params.getAcceptTypes()) if (t != null && t.startsWith("image")) wantsImage = true;
                if (params.isCaptureEnabled() && wantsImage) {              // <input capture> = 카메라 버튼
                    scanForChooser = true;                                  // 결과를 파일 선택 콜백으로
                    launchScanner();                                        // 앨범 대신 스캔 모드
                    return true;
                }
                try {
                    Intent intent = params.createIntent();                  // 기본 파일 선택기
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    try {                                                   // 대체 선택기
                        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                        fallback.addCategory(Intent.CATEGORY_OPENABLE);
                        fallback.setType("*/*");
                        startActivityForResult(Intent.createChooser(fallback, "파일 선택"), FILE_CHOOSER_REQUEST);
                        return true;
                    } catch (Exception ex) {
                        fileCallback = null;
                        Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri target = Uri.parse(url);                                    // 다운로드 주소
            if (!"https".equalsIgnoreCase(target.getScheme())) return;      // https만 처리
            try {
                DownloadManager.Request request = new DownloadManager.Request(target);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager != null) manager.enqueue(request);              // 다운로드 시작
            } catch (Exception error) {
                handleExternalUri(target);                                  // 실패 시 브라우저로
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState); // 회전 등 복원
        else webView.loadUrl(HOME);                                         // 첫 실행 로딩
    }

    /** 전화·메일·외부 링크를 다른 앱으로 실행 */
    private void handleExternalUri(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null) return;
        scheme = scheme.toLowerCase();

        Intent intent;
        if ("tel".equals(scheme)) intent = new Intent(Intent.ACTION_DIAL, uri);                 // 전화 앱
        else if ("mailto".equals(scheme) || "sms".equals(scheme)) intent = new Intent(Intent.ACTION_SENDTO, uri); // 메일/문자
        else if ("http".equals(scheme) || "https".equals(scheme)) intent = new Intent(Intent.ACTION_VIEW, uri);  // 브라우저
        else return;

        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "실행할 앱을 찾을 수 없습니다: " + uri, Toast.LENGTH_SHORT).show();
        }
    }

    // =====================================================================
    //  스캔 모드 (구글 문서 스캐너)
    // =====================================================================

    /** 문서 스캐너 실행: 자동 테두리 감지 + 자동 촬영 + 원근 보정 + 필터 */
    private void launchScanner() {
        if (scanInProgress) return;                                         // 중복 실행 방지
        scanInProgress = true;
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)                              // 앨범 사진도 스캔 보정 가능
                .setPageLimit(1)                                            // 명함 1장
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG) // JPEG 결과
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)    // 필터·지우개 포함 풀 모드
                .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options); // 스캐너 생성
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    try {
                        startIntentSenderForResult(intentSender, SCAN_REQUEST, null, 0, 0, 0); // 스캐너 화면 열기
                    } catch (IntentSender.SendIntentException e) {
                        scanInProgress = false;
                        finishChooser(null);
                        sendScanError("스캐너를 열 수 없습니다: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {                                // 구글 Play 서비스 문제 등
                    scanInProgress = false;
                    finishChooser(null);
                    sendScanError("스캔 모드를 사용할 수 없습니다(구글 Play 서비스 확인 필요). 앱 내 카메라로 전환합니다.");
                });
    }

    /** 한글 OCR 엔진 (처음 사용할 때 생성) */
    private synchronized TextRecognizer getRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()); // 한글+영문
        }
        return recognizer;
    }

    /** URI에서 비트맵 읽기 (긴 변 maxSide 이하로 축소) */
    private Bitmap decodeUri(Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();         // 크기만 먼저 읽기
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;                                                     // 축소 배율(2의 거듭제곱)
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);               // 실제 디코딩
        }
        if (bmp == null) throw new Exception("이미지를 읽을 수 없습니다");
        return scaleDown(bmp, maxSide);                                     // 정확한 크기로 축소
    }

    /** 긴 변을 maxSide 이하로 축소 */
    private Bitmap scaleDown(Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxSide) return src;                                // 이미 작으면 그대로
        float r = (float) maxSide / longSide;
        return Bitmap.createScaledBitmap(src, Math.round(w * r), Math.round(h * r), true);
    }

    /** 비트맵 → JPEG data URL */
    private String toDataUrl(Bitmap bmp, int maxWidth, int quality) {
        Bitmap out = bmp;
        if (bmp.getWidth() > maxWidth) {                                    // 가로 기준 축소
            float r = (float) maxWidth / bmp.getWidth();
            out = Bitmap.createScaledBitmap(bmp, maxWidth, Math.round(bmp.getHeight() * r), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, quality, bos);             // JPEG 압축
        return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    /** 기기 내 OCR 실행 → 웹으로 넘길 JSON (전체 텍스트 + 줄별 위치/크기) */
    private JSONObject runOcr(Bitmap bmp) throws Exception {
        Text result = Tasks.await(getRecognizer().process(InputImage.fromBitmap(bmp, 0))); // OCR 동기 실행
        JSONObject json = new JSONObject();
        json.put("text", result.getText());                                // 전체 텍스트
        json.put("width", bmp.getWidth());                                  // 이미지 가로
        json.put("height", bmp.getHeight());                                // 이미지 세로
        JSONArray lines = new JSONArray();
        for (Text.TextBlock block : result.getTextBlocks()) {               // 블록 단위
            for (Text.Line line : block.getLines()) {                       // 줄 단위
                JSONObject l = new JSONObject();
                l.put("text", line.getText());                              // 줄 텍스트
                Rect r = line.getBoundingBox();                             // 줄 영역
                if (r != null) {
                    l.put("x", r.left); l.put("y", r.top);                  // 위치
                    l.put("w", r.width()); l.put("h", r.height());          // 크기(글자 높이 → 이름 추정에 사용)
                }
                lines.put(l);
            }
        }
        json.put("lines", lines);                                           // 줄 목록
        json.put("engine", "mlkit-korean");                                 // 엔진 표시
        return json;
    }

    /** 스캔 결과 처리 (백그라운드): 이미지 읽기 → OCR → 웹 전달 */
    private void processScanResult(Uri imageUri) {
        callJs("window.__nativeScanStage && window.__nativeScanStage('ocr')"); // 웹에 "인식 중" 표시
        worker.execute(() -> {
            try {
                Bitmap bmp = decodeUri(imageUri, OCR_MAX_SIDE);              // 스캔 이미지 읽기
                JSONObject payload = runOcr(bmp);                           // 한글 OCR
                payload.put("ok", true);
                payload.put("image", toDataUrl(bmp, SAVE_MAX_WIDTH, 86));   // 저장용 이미지
                callJs("window.__nativeScanDone && window.__nativeScanDone(" + payload.toString() + ")");
            } catch (Exception e) {
                sendScanError("스캔 이미지 처리 실패: " + e.getMessage());
            }
        });
    }

    /** 스캔 오류/취소를 웹에 전달 */
    private void sendScanError(String message) {
        try {
            JSONObject err = new JSONObject();
            err.put("ok", false);
            err.put("error", message);
            callJs("window.__nativeScanDone && window.__nativeScanDone(" + err.toString() + ")");
        } catch (Exception ignored) {}
    }

    /** UI 스레드에서 JS 실행 */
    private void callJs(String script) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    // =====================================================================
    //  JS 브리지 (window.CardAndroid)
    // =====================================================================
    private class CardNativeBridge {
        /** 앱(APK) 안에서 실행 중인지 */
        @JavascriptInterface
        public boolean isNative() { return true; }

        /** 브리지 버전 (웹에서 기능 지원 여부 판단) */
        @JavascriptInterface
        public String getAppVersion() { return APP_VERSION; }

        /** 웹 화면에 맞춰 시스템바 색 변경 (예: 카메라 화면은 검정) */
        @JavascriptInterface
        public void setSystemBars(final String hex, final boolean darkIcons) {
            runOnUiThread(() -> {
                try { applySystemBars(Color.parseColor(hex), darkIcons); } catch (Exception ignored) {}
            });
        }

        /** 네이티브 스캔 모드 지원 여부 */
        @JavascriptInterface
        public boolean hasScanner() { return true; }

        /** 스캔 모드 시작 (결과는 window.__nativeScanDone 으로 전달) */
        @JavascriptInterface
        public void startScan() { runOnUiThread(MainActivity.this::launchScanner); }

        /**
         * 이미 가진 이미지(data URL)를 기기 내 한글 OCR로 인식
         * 결과: window.__nativeOcrDone(reqId, {ok, text, lines, width, height})
         */
        @JavascriptInterface
        public void recognizeText(final String reqId, final String dataUrl) {
            worker.execute(() -> {
                JSONObject payload;
                try {
                    String b64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);      // data URL 디코딩
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp == null) throw new Exception("이미지 디코딩 실패");
                    payload = runOcr(scaleDown(bmp, OCR_MAX_SIDE));          // OCR
                    payload.put("ok", true);
                } catch (Exception e) {
                    payload = new JSONObject();
                    try { payload.put("ok", false); payload.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
                }
                callJs("window.__nativeOcrDone && window.__nativeOcrDone(" + JSONObject.quote(reqId) + "," + payload.toString() + ")");
            });
        }

        /** 텍스트 파일(백업 JSON 등)을 다운로드/BusinessCards 폴더에 저장 후 공유창 표시 */
        @JavascriptInterface
        public void saveTextFile(String fileName, String mimeType, String content) {
            runOnUiThread(() -> {
                try {
                    String mime = (mimeType == null || mimeType.isEmpty()) ? "application/json" : mimeType;
                    Uri uri = writeDownload(fileName, mime, content.getBytes(StandardCharsets.UTF_8));
                    if (uri == null) throw new Exception("저장 위치를 만들 수 없습니다");
                    Toast.makeText(MainActivity.this, "다운로드/BusinessCards 폴더에 저장했습니다", Toast.LENGTH_LONG).show();
                    Intent share = new Intent(Intent.ACTION_SEND);           // 드라이브 등으로 보관 가능
                    share.setType(mime);
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "백업 파일 보관 (드라이브 등)"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "파일 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        /** 명함 사진을 사진 폴더에 저장하고 공유창 표시 (기존 기능 유지) */
        @JavascriptInterface
        public void shareOrSaveImage(String fileName, String base64Data) {
            if (base64Data == null || base64Data.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    String cleanBase64 = base64Data.contains(",")
                            ? base64Data.substring(base64Data.indexOf(",") + 1) : base64Data;
                    byte[] imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT);  // 이미지 바이트

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BusinessCards");
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    }
                    Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri == null) throw new Exception("MediaStore URI 생성 실패");
                    try (OutputStream os = getContentResolver().openOutputStream(imageUri)) {
                        if (os == null) throw new Exception("OutputStream 열기 실패");
                        os.write(imageBytes);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(imageUri, values, null, null);
                    }
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "구글 드라이브 명함 폴더: " + fileName);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "명함 사진 공유 (드라이브 선택)"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "사진 처리 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        /** vCard 저장 후 연락처 앱으로 열기 (기존 기능 유지) */
        @JavascriptInterface
        public void saveVcard(String name, String vcardText) {
            if (vcardText == null || vcardText.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    String filename = (name == null || name.isEmpty() ? "명함" : name) + ".vcf";
                    Uri vcfUri = writeDownload(filename, "text/x-vcard",
                            ("﻿" + vcardText).getBytes(StandardCharsets.UTF_8));
                    if (vcfUri != null) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(vcfUri, "text/x-vcard");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(intent);
                        } catch (Exception ex) {
                            Toast.makeText(MainActivity.this, "연락처가 다운로드 폴더에 저장되었습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "연락처 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** 웹 파일 선택 콜백 마무리 (스캔 결과 또는 취소) */
    private void finishChooser(Uri uri) {
        if (!scanForChooser) return;
        scanForChooser = false;
        if (fileCallback != null) {
            fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            fileCallback = null;
        }
    }

    /** 스캔 이미지를 앱 캐시로 복사 (웹뷰가 확실히 읽을 수 있게) */
    private Uri copyToCache(Uri src) {
        try {
            File out = new File(getCacheDir(), "scan_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(src);
                 FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384]; int n;
                while (in != null && (n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return Uri.fromFile(out);
        } catch (Exception e) {
            return src;                                                     // 복사 실패 시 원본 그대로
        }
    }

    /** 상단·하단 시스템바 색과 아이콘 밝기 */
    private void applySystemBars(int color, boolean darkIcons) {
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        int flags = 0;
        if (darkIcons) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** 다운로드/BusinessCards 폴더에 파일 쓰기 (안드로이드 10 이상) */
    private Uri writeDownload(String fileName, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;     // 구버전 미지원
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BusinessCards");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) os.write(bytes);                                // 내용 기록
        }
        return uri;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_REQUEST) {                                  // 스캐너 결과
            scanInProgress = false;
            if (resultCode == RESULT_OK && data != null) {
                GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
                if (result != null && result.getPages() != null && !result.getPages().isEmpty()) {
                    Uri page = result.getPages().get(0).getImageUri();      // 첫 페이지
                    if (scanForChooser) finishChooser(copyToCache(page));   // 웹 카메라 버튼 → 파일로 전달
                    else processScanResult(page);                           // 스캔 버튼 → OCR까지
                    return;
                }
                finishChooser(null);
                sendScanError("스캔 결과가 비어 있습니다");
            } else {
                if (scanForChooser) { finishChooser(null); return; }       // 파일 선택 취소
                try {                                                       // 사용자가 취소
                    JSONObject c = new JSONObject();
                    c.put("ok", false);
                    c.put("cancelled", true);
                    callJs("window.__nativeScanDone && window.__nativeScanDone(" + c.toString() + ")");
                } catch (Exception ignored) {}
            }
            return;
        }
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {  // 파일 선택 결과
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    final int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);                   // 웹뷰 상태 저장
        super.onSaveInstanceState(outState);
    }

    /** 뒤로가기: 웹에서 열린 화면(스캐너/시트)을 먼저 닫고, 없으면 기본 동작 */
    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(window.__onBack && window.__onBack()) ? 'handled' : 'no'", value -> {
            if (value != null && value.contains("handled")) return;         // 웹에서 처리함
            if (webView != null && webView.canGoBack()) webView.goBack();   // 웹 히스토리 뒤로
            else finish();                                                  // 앱 종료
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("CardAndroid");
            webView.destroy();
            webView = null;
        }
        if (recognizer != null) recognizer.close();                         // OCR 엔진 해제
        worker.shutdown();                                                  // 스레드 종료
        super.onDestroy();
    }
}
