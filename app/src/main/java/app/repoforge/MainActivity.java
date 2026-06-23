package app.repoforge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MainActivity extends Activity {
    private static final int REQUEST_TREE = 7101;

    private static final int BASE_BG = Color.rgb(7, 11, 18);
    private static final int CARD_BG = Color.rgb(10, 16, 25);
    private static final int CARD_BG_2 = Color.rgb(14, 22, 35);
    private static final int STROKE = Color.rgb(30, 49, 70);
    private static final int BRAND_CYAN = Color.rgb(45, 212, 255);
    private static final int BRAND_CYAN_SOFT = Color.rgb(184, 243, 255);
    private static final int TEXT_MAIN = Color.rgb(234, 242, 255);
    private static final int TEXT_MUTED = Color.rgb(145, 160, 181);
    private static final int SUCCESS = Color.rgb(36, 209, 139);
    private static final int WARNING = Color.rgb(251, 191, 36);
    private static final int DANGER = Color.rgb(255, 82, 82);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> logLines = new ArrayList<>();

    private Uri selectedTreeUri;
    private ArchiveMode activeMode = ArchiveMode.AI_AUDIT;
    private Uri resultUri;
    private Uri reportUri;
    private String selectedFolderName = "No source selected";
    private String resultName;
    private String reportName;
    private boolean busy;
    private boolean scanPreviewReady;
    private int detectedSecretCount;
    private ArrayList<String> detectedSecretExamples = new ArrayList<>();
    private ArrayList<String> includedExamples = new ArrayList<>();
    private ArrayList<String> skippedExamples = new ArrayList<>();

    private TextView selectedFolderView;
    private TextView modeValueView;
    private TextView rulesetView;
    private TextView securityView;
    private TextView historyView;
    private TextView scanDetailsView;
    private TextView readinessView;
    private TextView copyReadinessButton;
    private TextView copySummaryButton;
    private TextView modeAiButton;
    private TextView modeGithubButton;
    private TextView modeBackupButton;
    private TextView useLastButton;
    private TextView promptButton;
    private TextView statusView;
    private TextView fileStatView;
    private TextView skippedStatView;
    private TextView sourceStatView;
    private TextView writtenStatView;
    private TextView resultPathView;
    private TextView resultSummaryView;
    private TextView logView;
    private TextView scanButton;
    private TextView createButton;
    private TextView copyButton;
    private TextView shareButton;
    private TextView openButton;
    private LinearLayout resultPanel;
    private LinearLayout progressFill;
    private LinearLayout progressSpacer;

    private int totalFiles;
    private int zippedFiles;
    private int skippedItems;
    private long totalBytes;
    private long writtenBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBars(getWindow());
        restoreSavedMode();
        restoreSavedFolder();
        setContentView(createContentView());
        appendLog(selectedTreeUri == null ? "Select source → scan → build" : "Restored source: " + selectedFolderName);
        renderAll();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void setupBars(Window window) {
        window.setStatusBarColor(BASE_BG);
        window.setNavigationBarColor(BASE_BG);
    }

    private void restoreSavedMode() {
        String saved = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("mode", ArchiveMode.AI_AUDIT.name());
        activeMode = ArchiveMode.fromName(saved);
    }

    private void restoreSavedFolder() {
        String uriText = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("tree_uri", null);
        String nameText = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("tree_name", null);
        if (uriText != null && !uriText.trim().isEmpty()) {
            selectedTreeUri = Uri.parse(uriText);
            selectedFolderName = nameText == null || nameText.trim().isEmpty() ? "Saved folder" : nameText;
        }
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BASE_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeaderCard());
        addGap(root, 12);
        root.addView(createWorkflowCard());
        addGap(root, 12);
        root.addView(createStatusCard());
        addGap(root, 12);
        root.addView(createAnalysisCard());
        addGap(root, 12);
        resultPanel = createResultCard();
        root.addView(resultPanel);
        addGap(root, 12);
        root.addView(createSupportCard());
        addGap(root, 12);
        root.addView(createLogCard());

        return scrollView;
    }

    private View createHeaderCard() {
        LinearLayout card = panelBase(22, true);

        LinearLayout top = row(Gravity.CENTER_VERTICAL);
        top.addView(circle(DANGER, 8));
        addGapHorizontal(top, 6);
        top.addView(circle(WARNING, 8));
        addGapHorizontal(top, 6);
        top.addView(circle(SUCCESS, 8));
        addGapHorizontal(top, 12);
        top.addView(textMono("CLEAN PROJECT PACKAGER", TEXT_MUTED, 11, false));
        card.addView(top);

        addGap(card, 16);
        card.addView(textTitle("RepoForge"));
        addGap(card, 4);
        card.addView(textBody("Package clean source ZIPs for AI audit, GitHub and backup.", TEXT_MUTED, 14));
        addGap(card, 14);

        LinearLayout chips = row(Gravity.START);
        chips.addView(chip("OFFLINE"));
        addGapHorizontal(chips, 8);
        chips.addView(chip("NO ROOT"));
        addGapHorizontal(chips, 8);
        chips.addView(chip("v1.0"));
        card.addView(chips);
        return card;
    }

    private View createWorkflowCard() {
        LinearLayout card = panel("1 · PREPARE PACKAGE");

        card.addView(textMono("SOURCE FOLDER", TEXT_MUTED, 11, false));
        addGap(card, 6);
        selectedFolderView = textBody(selectedFolderName, TEXT_MAIN, 15);
        selectedFolderView.setSingleLine(false);
        card.addView(selectedFolderView);
        addGap(card, 12);

        LinearLayout selectRow = row(Gravity.START);
        TextView selectButton = button("SELECT SOURCE", false);
        useLastButton = button("USE LAST", false);
        selectButton.setOnClickListener(v -> openFolderPicker());
        useLastButton.setOnClickListener(v -> useLastSource());
        selectRow.addView(selectButton);
        addGapHorizontal(selectRow, 10);
        selectRow.addView(useLastButton);
        card.addView(selectRow);

        addGap(card, 14);
        modeValueView = textMono("MODE: " + activeMode.title, BRAND_CYAN_SOFT, 12, true);
        modeValueView.setTextIsSelectable(false);
        card.addView(modeValueView);
        addGap(card, 10);

        LinearLayout modeRow1 = row(Gravity.START);
        modeAiButton = button("AI AUDIT", true);
        modeGithubButton = button("GITHUB CLEAN", false);
        modeAiButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.AI_AUDIT));
        modeGithubButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.GITHUB_CLEAN));
        modeRow1.addView(modeAiButton);
        addGapHorizontal(modeRow1, 10);
        modeRow1.addView(modeGithubButton);
        card.addView(modeRow1);
        addGap(card, 10);

        LinearLayout modeRow2 = row(Gravity.START);
        modeBackupButton = button("BACKUP SAFE", false);
        modeBackupButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.BACKUP_SAFE));
        modeRow2.addView(modeBackupButton);
        card.addView(modeRow2);

        addGap(card, 14);
        LinearLayout actionRow = row(Gravity.START);
        scanButton = button("SCAN SOURCE", false);
        createButton = button("BUILD PACKAGE", true);
        scanButton.setOnClickListener(v -> startScanOnly());
        createButton.setOnClickListener(v -> startZipBuild());
        actionRow.addView(scanButton);
        addGapHorizontal(actionRow, 10);
        actionRow.addView(createButton);
        card.addView(actionRow);

        return card;
    }

    private View createAnalysisCard() {
        LinearLayout card = panel("3 · AI READY CHECK");

        readinessView = textMono("No scan yet", TEXT_MUTED, 12, true);
        readinessView.setTextIsSelectable(true);
        card.addView(readinessView);
        addGap(card, 12);

        securityView = textMono("No scan yet", TEXT_MUTED, 12, true);
        securityView.setTextIsSelectable(true);
        card.addView(securityView);
        addGap(card, 12);

        scanDetailsView = textMono("No scan yet", TEXT_MUTED, 12, true);
        scanDetailsView.setTextIsSelectable(true);
        card.addView(scanDetailsView);
        addGap(card, 12);

        LinearLayout row1 = row(Gravity.START);
        copyReadinessButton = button("COPY READINESS", false);
        promptButton = button("COPY AI PROMPT", false);
        copyReadinessButton.setOnClickListener(v -> copyReadiness());
        promptButton.setOnClickListener(v -> copyAiAuditPrompt());
        row1.addView(copyReadinessButton);
        addGapHorizontal(row1, 10);
        row1.addView(promptButton);
        card.addView(row1);
        return card;
    }

    private View createSupportCard() {
        LinearLayout card = panel("HISTORY");
        card.addView(textBody("Recently created packages.", TEXT_MUTED, 14));
        addGap(card, 12);

        historyView = textMono(loadHistoryText(), TEXT_MUTED, 12, true);
        historyView.setTextIsSelectable(true);
        card.addView(historyView);
        addGap(card, 12);

        LinearLayout row1 = row(Gravity.START);
        TextView historyCopyButton = button("COPY HISTORY", false);
        TextView historyClearButton = button("CLEAR HISTORY", false);
        historyCopyButton.setOnClickListener(v -> copyHistory());
        historyClearButton.setOnClickListener(v -> clearHistory());
        row1.addView(historyCopyButton);
        addGapHorizontal(row1, 10);
        row1.addView(historyClearButton);
        card.addView(row1);

        return card;
    }


    private View createModeCard() {
        LinearLayout card = panel("ARCHIVE MODE");
        card.addView(textBody("Choose how aggressive RepoForge should clean the project before packaging.", TEXT_MUTED, 14));
        addGap(card, 8);
        card.addView(textMono("AI AUDIT     : best for Claude / ChatGPT\nGITHUB CLEAN : best for source release review\nBACKUP SAFE  : broader local backup without secrets", TEXT_MUTED, 11, true));
        addGap(card, 12);

        modeValueView = textMono("MODE: " + activeMode.title, BRAND_CYAN_SOFT, 12, true);
        modeValueView.setTextIsSelectable(false);
        card.addView(modeValueView);
        addGap(card, 12);

        LinearLayout row1 = row(Gravity.START);
        modeAiButton = button("AI AUDIT", true);
        modeGithubButton = button("GITHUB CLEAN", false);
        row1.addView(modeAiButton);
        addGapHorizontal(row1, 10);
        row1.addView(modeGithubButton);
        card.addView(row1);
        addGap(card, 10);

        LinearLayout row2 = row(Gravity.START);
        modeBackupButton = button("BACKUP SAFE", false);
        row2.addView(modeBackupButton);
        card.addView(row2);

        modeAiButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.AI_AUDIT));
        modeGithubButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.GITHUB_CLEAN));
        modeBackupButton.setOnClickListener(v -> setArchiveMode(ArchiveMode.BACKUP_SAFE));
        return card;
    }

    private View createProjectCard() {
        LinearLayout card = panel("SOURCE ROOT");
        card.addView(textMono("SELECTED SOURCE", TEXT_MUTED, 11, false));
        addGap(card, 8);
        selectedFolderView = textBody(selectedFolderName, TEXT_MAIN, 15);
        selectedFolderView.setSingleLine(false);
        card.addView(selectedFolderView);
        addGap(card, 14);

        LinearLayout buttons = row(Gravity.START);
        TextView selectButton = button("SELECT SOURCE", false);
        useLastButton = button("USE LAST SOURCE", false);
        selectButton.setOnClickListener(v -> openFolderPicker());
        useLastButton.setOnClickListener(v -> useLastSource());
        buttons.addView(selectButton);
        addGapHorizontal(buttons, 10);
        buttons.addView(useLastButton);
        card.addView(buttons);
        addGap(card, 10);

        LinearLayout buildRow = row(Gravity.START);
        scanButton = button("SCAN SOURCE", false);
        createButton = button("BUILD PACKAGE", true);
        scanButton.setOnClickListener(v -> startScanOnly());
        createButton.setOnClickListener(v -> startZipBuild());
        buildRow.addView(scanButton);
        addGapHorizontal(buildRow, 10);
        buildRow.addView(createButton);
        card.addView(buildRow);

        return card;
    }

    private View createStatusCard() {
        LinearLayout card = panel("2 · STATUS");
        statusView = textBody("Ready", TEXT_MAIN, 15);
        card.addView(statusView);
        addGap(card, 14);

        LinearLayout row1 = row(Gravity.START);
        fileStatView = statBox(row1, "FILES", "0/0");
        addGapHorizontal(row1, 8);
        skippedStatView = statBox(row1, "SKIPPED", "0");
        card.addView(row1);
        addGap(card, 8);

        LinearLayout row2 = row(Gravity.START);
        sourceStatView = statBox(row2, "SOURCE", "0 B");
        addGapHorizontal(row2, 8);
        writtenStatView = statBox(row2, "WRITTEN", "0 B");
        card.addView(row2);
        addGap(card, 14);

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(round(Color.rgb(8, 17, 29), STROKE, 999));
        track.setPadding(0, 0, 0, 0);
        card.addView(track, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        progressFill = new LinearLayout(this);
        progressFill.setBackground(round(BRAND_CYAN, BRAND_CYAN, 999));
        progressSpacer = new LinearLayout(this);
        track.addView(progressFill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressSpacer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        return card;
    }

    private View createScanDetailsCard() {
        LinearLayout card = panel("SCAN DETAILS");
        card.addView(textBody("Preview what will be included and what RepoForge skipped before you send the archive to an AI reviewer.", TEXT_MUTED, 14));
        addGap(card, 12);
        scanDetailsView = textMono("No scan yet", TEXT_MUTED, 12, true);
        scanDetailsView.setTextIsSelectable(true);
        card.addView(scanDetailsView);
        return card;
    }

    private View createReadinessCard() {
        LinearLayout card = panel("AI READINESS");
        card.addView(textBody("A compact preflight score for AI upload readiness: size, mode, security hygiene and package density.", TEXT_MUTED, 14));
        addGap(card, 12);
        readinessView = textMono("No scan yet", TEXT_MUTED, 12, true);
        readinessView.setTextIsSelectable(true);
        card.addView(readinessView);
        addGap(card, 12);
        copyReadinessButton = button("COPY READINESS", false);
        copyReadinessButton.setOnClickListener(v -> copyReadiness());
        card.addView(copyReadinessButton);
        return card;
    }

    private View createSecurityCard() {
        LinearLayout card = panel("SECURITY CHECK");
        card.addView(textBody("RepoForge checks for known signing keys, .env files, service accounts and local machine configuration before packaging.", TEXT_MUTED, 14));
        addGap(card, 12);
        securityView = textMono("No scan yet", TEXT_MUTED, 12, true);
        securityView.setTextIsSelectable(true);
        card.addView(securityView);
        return card;
    }

    private View createHistoryCard() {
        LinearLayout card = panel("PACKAGE HISTORY");
        historyView = textMono(loadHistoryText(), TEXT_MUTED, 12, true);
        historyView.setTextIsSelectable(true);
        card.addView(historyView);
        addGap(card, 12);
        LinearLayout row = row(Gravity.START);
        TextView copyHistoryButton = button("COPY HISTORY", false);
        TextView clearHistoryButton = button("CLEAR HISTORY", false);
        copyHistoryButton.setOnClickListener(v -> copyHistory());
        clearHistoryButton.setOnClickListener(v -> clearHistory());
        row.addView(copyHistoryButton);
        addGapHorizontal(row, 10);
        row.addView(clearHistoryButton);
        card.addView(row);
        return card;
    }

    private View createExclusionsCard() {
        LinearLayout card = panel("ACTIVE CLEAN RULESET");
        card.addView(textBody("Rules are generated from the selected archive mode. Secret files and signing material are always excluded.", TEXT_MUTED, 14));
        addGap(card, 12);
        rulesetView = textMono(buildRulesetText(), TEXT_MUTED, 12, true);
        rulesetView.setTextIsSelectable(true);
        card.addView(rulesetView);
        return card;
    }

    private LinearLayout createResultCard() {
        LinearLayout card = panel("4 · OUTPUT");
        card.addView(textMono("PACKAGE PATH", TEXT_MUTED, 11, false));
        addGap(card, 8);
        resultPathView = textBody("Downloads/RepoForge/", SUCCESS, 14);
        resultPathView.setTextIsSelectable(true);
        resultPathView.setSingleLine(false);
        card.addView(resultPathView);
        addGap(card, 12);

        resultSummaryView = textMono("No package built yet", TEXT_MUTED, 12, true);
        resultSummaryView.setTextIsSelectable(true);
        card.addView(resultSummaryView);
        addGap(card, 14);

        LinearLayout row1 = row(Gravity.START);
        shareButton = button("SHARE LATEST", true);
        openButton = button("OPEN OUTPUT", false);
        shareButton.setOnClickListener(v -> shareResultZip());
        openButton.setOnClickListener(v -> openOutputFolder());
        row1.addView(shareButton);
        addGapHorizontal(row1, 10);
        row1.addView(openButton);
        card.addView(row1);
        addGap(card, 10);

        LinearLayout row2 = row(Gravity.START);
        copyButton = button("COPY PATH", false);
        copySummaryButton = button("COPY SUMMARY", false);
        copyButton.setOnClickListener(v -> copyResultPath());
        copySummaryButton.setOnClickListener(v -> copyResultSummary());
        row2.addView(copyButton);
        addGapHorizontal(row2, 10);
        row2.addView(copySummaryButton);
        card.addView(row2);

        card.setVisibility(View.GONE);
        return card;
    }

    private View createLogCard() {
        LinearLayout card = panel("TECH LOG");
        logView = textMono("No log entries", TEXT_MUTED, 12, true);
        logView.setTextIsSelectable(true);
        card.addView(logView);
        addGap(card, 12);
        TextView clearButton = button("CLEAR LOG", false);
        clearButton.setOnClickListener(v -> {
            logLines.clear();
            renderLog();
        });
        card.addView(clearButton);
        return card;
    }

    private void setArchiveMode(ArchiveMode mode) {
        if (mode == null || busy) return;
        activeMode = mode;
        scanPreviewReady = false;
        detectedSecretCount = 0;
        detectedSecretExamples.clear();
        includedExamples.clear();
        skippedExamples.clear();
        resultUri = null;
        reportUri = null;
        resultName = null;
        reportName = null;
        getSharedPreferences("repoforge_state", MODE_PRIVATE).edit().putString("mode", activeMode.name()).apply();
        appendLog("Mode selected: " + activeMode.title);
        setStatus("Mode: " + activeMode.title);
        renderAll();
    }

    private void useLastSource() {
        String uriText = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("tree_uri", null);
        String nameText = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("tree_name", null);
        if (uriText == null || uriText.trim().isEmpty()) {
            appendLog("No saved source root");
            setStatus("No saved source root");
            return;
        }
        selectedTreeUri = Uri.parse(uriText);
        selectedFolderName = nameText == null || nameText.trim().isEmpty() ? "Saved folder" : nameText;
        resultUri = null;
        reportUri = null;
        resultName = null;
        reportName = null;
        scanPreviewReady = false;
        detectedSecretCount = 0;
        detectedSecretExamples.clear();
        appendLog("Using saved source: " + selectedFolderName);
        setStatus("Saved source loaded");
        renderAll();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TREE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            appendLog("Source selection cancelled");
            return;
        }

        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            appendLog("Warning: folder permission was not persisted by system");
        }

        selectedTreeUri = uri;
        selectedFolderName = queryRootName(uri);
        resultUri = null;
        reportUri = null;
        resultName = null;
        reportName = null;
        scanPreviewReady = false;
        detectedSecretCount = 0;
        detectedSecretExamples.clear();
        getSharedPreferences("repoforge_state", MODE_PRIVATE)
                .edit()
                .putString("tree_uri", uri.toString())
                .putString("tree_name", selectedFolderName)
                .apply();

        appendLog("Source selected: " + selectedFolderName);
        renderAll();
    }

    private String queryRootName(Uri treeUri) {
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
            String name = queryString(rootDocUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (Exception ignored) {
        }
        return "Selected source";
    }

    private String queryString(Uri uri, String column) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{column}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private void startScanOnly() {
        if (busy) return;
        if (selectedTreeUri == null) {
            appendLog("Select source root first");
            setStatus("Source root is not selected");
            return;
        }

        busy = true;
        scanPreviewReady = false;
        totalFiles = 0;
        zippedFiles = 0;
        skippedItems = 0;
        totalBytes = 0L;
        writtenBytes = 0L;
        detectedSecretCount = 0;
        detectedSecretExamples.clear();
        includedExamples.clear();
        skippedExamples.clear();
        resultUri = null;
        reportUri = null;
        resultName = null;
        reportName = null;
        logLines.clear();
        appendLog("Preflight scan started");
        setStatus("Scanning source tree");
        renderAll();

        executor.execute(() -> {
            try {
                ScanResult scan = scanTree(selectedTreeUri);
                mainHandler.post(() -> {
                    busy = false;
                    scanPreviewReady = true;
                    totalFiles = scan.filesCount;
                    zippedFiles = 0;
                    skippedItems = scan.skippedCount;
                    totalBytes = scan.totalBytes;
                    writtenBytes = 0L;
                    applyScanInsights(scan);
                    if (scan.entries.isEmpty()) {
                        setStatus("Scan complete: nothing to package");
                        appendLog("Nothing to package after exclusions");
                    } else {
                        setStatus("Scan complete");
                        appendLog("Ready: " + scan.filesCount + " source files, " + scan.skippedCount + " skipped, " + formatBytes(scan.totalBytes));
                        appendLog("Build package when the scan result looks correct");
                    }
                    renderAll();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    busy = false;
                    scanPreviewReady = false;
                    setStatus("Failed: " + safeMessage(error));
                    appendLog("Scan error: " + safeMessage(error));
                    renderAll();
                });
            }
        });
    }

    private void startZipBuild() {
        if (busy) return;
        if (selectedTreeUri == null) {
            appendLog("Select source root first");
            setStatus("Source root is not selected");
            return;
        }

        busy = true;
        scanPreviewReady = false;
        totalFiles = 0;
        zippedFiles = 0;
        skippedItems = 0;
        totalBytes = 0L;
        writtenBytes = 0L;
        detectedSecretCount = 0;
        detectedSecretExamples.clear();
        includedExamples.clear();
        skippedExamples.clear();
        resultUri = null;
        reportUri = null;
        resultName = null;
        reportName = null;
        logLines.clear();
        appendLog("Source scan started");
        setStatus("Scanning source tree");
        renderAll();

        executor.execute(() -> {
            try {
                ZipBuildResult result = buildCleanZip(selectedTreeUri);
                mainHandler.post(() -> {
                    busy = false;
                    resultUri = result.uri;
                    reportUri = result.reportUri;
                    resultName = result.displayName;
                    reportName = result.reportName;
                    applyScanInsights(result.scanResult);
                    saveHistory(result.displayName, result.reportName);
                    setStatus("Package created");
                    appendLog("Done: " + result.displayName);
                    if (result.reportName != null) appendLog("Report: " + result.reportName);
                    renderAll();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    busy = false;
                    setStatus("Failed: " + safeMessage(error));
                    appendLog("Error: " + safeMessage(error));
                    renderAll();
                });
            }
        });
    }

    private ZipBuildResult buildCleanZip(Uri treeUri) throws Exception {
        postStatus("Scanning source files");
        postLog("Mode: " + activeMode.title);
        postLog("Rules: " + activeMode.summary);

        ScanResult scan = scanTree(treeUri);
        if (scan.entries.isEmpty()) throw new IllegalStateException("Nothing to package after exclusions");

        String rootName = sanitizeFileName(queryRootName(treeUri));
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        String zipName = rootName + "_repoforge_" + activeMode.slug + "_" + stamp + ".zip";
        String reportFileName = rootName + "_repoforge_report_" + activeMode.slug + "_" + stamp + ".md";

        postStats(scan.filesCount, 0, scan.skippedCount, scan.totalBytes, 0L);
        postStatus("Preparing package");
        postLog("Source: " + scan.filesCount + " files, skipped " + scan.skippedCount + " items, size " + formatBytes(scan.totalBytes));

        ZipBuildResult target = createOutputTarget(zipName);
        boolean ok = false;
        try {
            ContentResolver resolver = getContentResolver();
            OutputStream outputStream = resolver.openOutputStream(target.uri);
            if (outputStream == null) throw new IllegalStateException("Cannot open output package");

            byte[] buffer = new byte[128 * 1024];
            long bytes = 0L;
            int index = 0;

            ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(outputStream, 128 * 1024));
            try {
                for (ArchiveEntry entry : scan.entries) {
                    index++;
                    postStatus("Archiving " + index + "/" + scan.filesCount);
                    postStats(scan.filesCount, index, scan.skippedCount, scan.totalBytes, bytes);
                    if (index <= 12 || index % 50 == 0 || index == scan.filesCount) {
                        postLog("Add: " + entry.relativePath);
                    }

                    InputStream rawInput = resolver.openInputStream(entry.uri);
                    if (rawInput == null) throw new IllegalStateException("Cannot read " + entry.relativePath);

                    BufferedInputStream input = new BufferedInputStream(rawInput, 128 * 1024);
                    try {
                        zipOut.putNextEntry(new ZipEntry(entry.relativePath));
                        while (true) {
                            int read = input.read(buffer);
                            if (read < 0) break;
                            zipOut.write(buffer, 0, read);
                            bytes += read;
                        }
                        zipOut.closeEntry();
                    } finally {
                        input.close();
                    }
                }

                byte[] auditNote = buildAuditNoteText(rootName, zipName, scan).getBytes(StandardCharsets.UTF_8);
                zipOut.putNextEntry(new ZipEntry("REPOFORGE_AUDIT_NOTE.md"));
                zipOut.write(auditNote);
                zipOut.closeEntry();
                bytes += auditNote.length;
                postLog("Add: REPOFORGE_AUDIT_NOTE.md");
            } finally {
                zipOut.close();
            }

            writtenBytes = bytes;
            finalizeOutputTarget(target.uri);
            ok = true;
            postStats(scan.filesCount, scan.filesCount, scan.skippedCount, scan.totalBytes, bytes);
            postStatus("Finalizing package");
            postLog("Written " + formatBytes(bytes));

            Uri finalReportUri = null;
            String finalReportName = null;
            try {
                ZipBuildResult reportTarget = createOutputTarget(reportFileName, "text/markdown");
                writeReportFile(reportTarget.uri, buildReportText(rootName, zipName, scan, bytes));
                finalizeOutputTarget(reportTarget.uri);
                finalReportUri = reportTarget.uri;
                finalReportName = reportTarget.displayName;
            } catch (Exception reportError) {
                postLog("Report skipped: " + safeMessage(reportError));
            }

            return new ZipBuildResult(target.uri, target.displayName, finalReportUri, finalReportName, scan);
        } finally {
            if (!ok) {
                try {
                    getContentResolver().delete(target.uri, null, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private ScanResult scanTree(Uri treeUri) throws Exception {
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        ArrayList<ArchiveEntry> entries = new ArrayList<>();
        MutableStats stats = new MutableStats();
        scanChildren(treeUri, rootId, "", entries, stats);
        Collections.sort(entries, Comparator.comparing(entry -> entry.relativePath.toLowerCase(Locale.US)));
        ArrayList<String> includedPreview = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < 12; i++) {
            includedPreview.add(entries.get(i).relativePath);
        }
        return new ScanResult(entries, stats.skippedCount, stats.filesCount, stats.totalBytes, stats.secretCount, stats.secretExamples, stats.skippedExamples, includedPreview);
    }

    private void scanChildren(Uri treeUri, String parentDocumentId, String prefix, ArrayList<ArchiveEntry> entries, MutableStats stats) throws Exception {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE
                    },
                    null,
                    null,
                    null
            );
            if (cursor == null) return;

            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);

            while (cursor.moveToNext()) {
                String documentId = cursor.getString(idIndex);
                String name = cursor.getString(nameIndex);
                String mime = cursor.getString(mimeIndex);
                long size = 0L;
                if (!cursor.isNull(sizeIndex)) size = Math.max(0L, cursor.getLong(sizeIndex));

                if (name == null || name.trim().isEmpty()) {
                    stats.skippedCount++;
                    continue;
                }

                boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                String relativePath = prefix.length() == 0 ? name : prefix + "/" + name;

                boolean securitySensitive = DefaultExclusions.isSecuritySensitive(name, relativePath, isDirectory);
                if (DefaultExclusions.shouldSkip(name, relativePath, isDirectory, activeMode)) {
                    stats.skippedCount++;
                    if (stats.skippedExamples.size() < 12) stats.skippedExamples.add(relativePath);
                    if (securitySensitive) {
                        stats.secretCount++;
                        if (stats.secretExamples.size() < 12) stats.secretExamples.add(relativePath);
                    }
                    continue;
                }

                if (isDirectory) {
                    scanChildren(treeUri, documentId, relativePath, entries, stats);
                } else {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    entries.add(new ArchiveEntry(documentUri, relativePath, size));
                    stats.filesCount++;
                    stats.totalBytes += size;
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private ZipBuildResult createOutputTarget(String displayName) {
        return createOutputTarget(displayName, "application/zip");
    }

    private ZipBuildResult createOutputTarget(String displayName, String mimeType) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RepoForge");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore refused output target");
        return new ZipBuildResult(uri, displayName);
    }

    private void writeReportFile(Uri uri, String reportText) throws Exception {
        OutputStream stream = getContentResolver().openOutputStream(uri);
        if (stream == null) throw new IllegalStateException("Cannot open report output");
        OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        try {
            writer.write(reportText);
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private String buildReportText(String rootName, String zipName, ScanResult scan, long outputBytes) {
        String created = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder builder = new StringBuilder();
        builder.append("# RepoForge Build Report\n\n");
        builder.append("## Source\n\n");
        builder.append("- Project: `").append(rootName).append("`\n");
        builder.append("- Created: `").append(created).append("`\n");
        builder.append("- Mode: `").append(activeMode.title).append("`\n");
        builder.append("- Package: `").append(zipName).append("`\n");
        builder.append("- Output: `Downloads/RepoForge/").append(zipName).append("`\n\n");
        builder.append("## Stats\n\n");
        builder.append("| Metric | Value |\n");
        builder.append("|---|---:|\n");
        builder.append("| Included files | ").append(scan.filesCount).append(" |\n");
        builder.append("| Skipped items | ").append(scan.skippedCount).append(" |\n");
        builder.append("| Source size | ").append(formatBytes(scan.totalBytes)).append(" |\n");
        builder.append("| Written size | ").append(formatBytes(outputBytes)).append(" |\n\n");
        builder.append("## AI readiness\n\n");
        builder.append(buildReadinessTextFor(scan.filesCount, scan.totalBytes, scan.secretCount, scan.skippedCount, true)).append("\n\n");
        builder.append("## Security check\n\n");
        if (scan.secretCount > 0) {
            builder.append("Potential secret files detected and excluded: `").append(scan.secretCount).append("`\n\n");
            for (String example : scan.secretExamples) {
                builder.append("- `").append(example).append("`\n");
            }
            builder.append("\n");
        } else {
            builder.append("No known secret filenames were detected during scan.\n\n");
        }
        builder.append("## Size warning\n\n");
        builder.append(buildSizeWarning(scan.totalBytes)).append("\n\n");
        builder.append("## Included preview\n\n");
        if (scan.includedExamples.isEmpty()) {
            builder.append("No included examples captured.\n");
        } else {
            for (String example : scan.includedExamples) builder.append("- `").append(example).append("`\n");
        }
        builder.append("\n## Skipped preview\n\n");
        if (scan.skippedExamples.isEmpty()) {
            builder.append("No skipped examples captured.\n");
        } else {
            for (String example : scan.skippedExamples) builder.append("- `").append(example).append("`\n");
        }
        builder.append("\n## Active clean ruleset\n\n");
        for (String rule : DefaultExclusions.describeRules(activeMode)) {
            builder.append("- ").append(rule).append("\n");
        }
        builder.append("\n## AI audit note\n\n");
        builder.append("This archive was generated by RepoForge. Generated outputs, Gradle caches, local SDK paths and known secret files were excluded according to the selected mode.\n");
        return builder.toString();
    }

    private String buildAuditNoteText(String rootName, String zipName, ScanResult scan) {
        String created = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder builder = new StringBuilder();
        builder.append("# RepoForge Audit Note\n\n");
        builder.append("This archive was generated by RepoForge in `").append(activeMode.title).append("` mode.\n\n");
        builder.append("## Package\n\n");
        builder.append("- Project: `").append(rootName).append("`\n");
        builder.append("- Archive: `").append(zipName).append("`\n");
        builder.append("- Created: `").append(created).append("`\n");
        builder.append("- Included source files: `").append(scan.filesCount).append("`\n");
        builder.append("- Skipped items: `").append(scan.skippedCount).append("`\n\n");
        builder.append("## AI readiness\n\n");
        builder.append(buildReadinessTextFor(scan.filesCount, scan.totalBytes, scan.secretCount, scan.skippedCount, true)).append("\n\n");
        builder.append("## Cleaning summary\n\n");
        builder.append("Generated outputs, Gradle caches, APK artifacts, local SDK paths, signing keys and known secret files were excluded according to the active ruleset.\n");
        if (scan.secretCount > 0) {
            builder.append("\nPotential secret files detected and excluded: `").append(scan.secretCount).append("`.\n");
        }
        builder.append("\n## Scan preview\n\n");
        builder.append("Included examples:\n");
        for (String example : scan.includedExamples) builder.append("- `").append(example).append("`\n");
        builder.append("\nSkipped examples:\n");
        for (String example : scan.skippedExamples) builder.append("- `").append(example).append("`\n");
        return builder.toString();
    }

    private void finalizeOutputTarget(Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
    }

    private void copyReadiness() {
        if (!hasScanState()) return;
        StringBuilder text = new StringBuilder();
        text.append("# RepoForge AI Readiness\n\n");
        text.append(buildReadinessText());
        text.append("\n\n");
        text.append("Prompt:\n");
        text.append("Analyze this Android project archive generated by RepoForge. Focus on architecture, Gradle setup, security, UI consistency, dead code, build risks and practical improvements. Give a prioritized fix plan.");
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("RepoForge readiness", text.toString()));
        appendLog("Copied AI readiness");
        Toast.makeText(this, "AI readiness copied", Toast.LENGTH_SHORT).show();
    }

    private void copyResultSummary() {
        if (resultName == null) return;
        StringBuilder text = new StringBuilder();
        text.append("# RepoForge Package Summary\n\n");
        text.append("- Mode: ").append(activeMode.title).append('\n');
        text.append("- Source: ").append(selectedFolderName).append('\n');
        text.append("- Archive: Downloads/RepoForge/").append(resultName).append('\n');
        if (reportName != null) text.append("- Report: Downloads/RepoForge/").append(reportName).append('\n');
        text.append("- Included files: ").append(zippedFiles).append('/').append(totalFiles).append('\n');
        text.append("- Skipped items: ").append(skippedItems).append('\n');
        text.append("- Source size: ").append(formatBytes(totalBytes)).append('\n');
        text.append("- Written size: ").append(formatBytes(writtenBytes)).append("\n\n");
        text.append("Security: ");
        if (detectedSecretCount > 0) {
            text.append(detectedSecretCount).append(" potential secret files were detected and excluded.\n");
        } else {
            text.append("no known secret filenames detected.\n");
        }
        text.append("\nAI readiness:\n");
        text.append(buildReadinessText()).append("\n");
        text.append("\nAI prompt:\n");
        text.append("Analyze this Android project archive generated by RepoForge. Check architecture, Gradle setup, security, UI consistency, dead code, build risks, packaging hygiene and practical improvements. Give a concrete prioritized fix plan.");
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("RepoForge package summary", text.toString()));
        appendLog("Copied package summary");
        Toast.makeText(this, "Package summary copied", Toast.LENGTH_SHORT).show();
    }

    private void copyResultPath() {
        if (resultName == null) return;
        StringBuilder text = new StringBuilder();
        text.append("Downloads/RepoForge/").append(resultName);
        if (reportName != null) {
            text.append("\nDownloads/RepoForge/").append(reportName);
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("RepoForge result", text.toString()));
        appendLog("Copied output path");
        Toast.makeText(this, "Output path copied", Toast.LENGTH_SHORT).show();
    }

    private void copyAiAuditPrompt() {
        String prompt = "Analyze this Android project archive generated by RepoForge. Check architecture, Gradle setup, security, UI consistency, dead code, build risks, packaging hygiene and practical improvements. Give a concrete prioritized fix plan. Do not rewrite the whole project unless absolutely necessary. Archive mode: " + activeMode.title + ".";
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("RepoForge AI audit prompt", prompt));
        appendLog("Copied AI audit prompt");
        Toast.makeText(this, "AI audit prompt copied", Toast.LENGTH_SHORT).show();
    }






    private void deleteLatestPackage() {
        if (resultUri == null) return;
        try {
            getContentResolver().delete(resultUri, null, null);
            if (reportUri != null) getContentResolver().delete(reportUri, null, null);
            appendLog("Deleted latest package output");
            resultUri = null;
            reportUri = null;
            resultName = null;
            reportName = null;
            writtenBytes = 0L;
            renderAll();
            Toast.makeText(this, "Latest package deleted", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            appendLog("Delete failed: " + safeMessage(error));
            Toast.makeText(this, "Delete failed: " + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void shareResultZip() {
        if (resultUri == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, resultUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share RepoForge package"));
    }

    private void openOutputFolder() {
        Intent folderIntent = new Intent(Intent.ACTION_VIEW);
        folderIntent.setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FRepoForge"),
                "vnd.android.document/directory"
        );
        folderIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(folderIntent);
            return;
        } catch (Exception ignored) {
        }

        if (resultUri != null) {
            Intent zipIntent = new Intent(Intent.ACTION_VIEW);
            zipIntent.setDataAndType(resultUri, "application/zip");
            zipIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(zipIntent);
                return;
            } catch (Exception ignored) {
            }
        }

        Toast.makeText(this, "Open Downloads/RepoForge manually", Toast.LENGTH_LONG).show();
        appendLog("Open output failed: use Downloads/RepoForge");
    }

    private void applyScanInsights(ScanResult scan) {
        detectedSecretCount = scan.secretCount;
        detectedSecretExamples.clear();
        detectedSecretExamples.addAll(scan.secretExamples);
        includedExamples.clear();
        includedExamples.addAll(scan.includedExamples);
        skippedExamples.clear();
        skippedExamples.addAll(scan.skippedExamples);
        if (scan.secretCount > 0) {
            appendLog("Security: " + scan.secretCount + " potential secret files excluded");
        }
        String warning = buildSizeWarning(scan.totalBytes);
        if (scan.totalBytes >= 50L * 1024L * 1024L) appendLog(warning);
    }

    private String buildScanDetailsText() {
        if (totalFiles == 0 && skippedItems == 0 && totalBytes == 0L && resultName == null && !scanPreviewReady) {
            return "No scan yet\nNext: select a source and tap SCAN SOURCE";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Mode: ").append(activeMode.title).append('\n');
        builder.append("Included: ").append(totalFiles).append(" files · ").append(formatBytes(totalBytes)).append('\n');
        builder.append("Skipped: ").append(skippedItems).append(" items\n\n");
        builder.append("Included preview:\n");
        if (includedExamples.isEmpty()) {
            builder.append("- no included examples yet\n");
        } else {
            for (String item : includedExamples) builder.append("+ ").append(item).append('\n');
            if (totalFiles > includedExamples.size()) builder.append("+ ... +").append(totalFiles - includedExamples.size()).append(" more files\n");
        }
        builder.append("\nSkipped preview:\n");
        if (skippedExamples.isEmpty()) {
            builder.append("- no skipped examples yet\n");
        } else {
            for (String item : skippedExamples) builder.append("- ").append(item).append('\n');
            if (skippedItems > skippedExamples.size()) builder.append("- ... +").append(skippedItems - skippedExamples.size()).append(" more skipped items\n");
        }
        return builder.toString().trim();
    }

    private String buildSecurityText() {
        if (totalFiles == 0 && skippedItems == 0 && totalBytes == 0L && resultName == null && !scanPreviewReady) {
            return "Security: waiting for scan\nChecks: secrets, keys, local paths";
        }
        StringBuilder builder = new StringBuilder();
        if (detectedSecretCount > 0) {
            builder.append("Security: protected\n");
            builder.append("Excluded: ").append(detectedSecretCount).append(" potential secret files\n");
            for (String item : detectedSecretExamples) {
                builder.append("- ").append(item).append("\n");
            }
            if (detectedSecretCount > detectedSecretExamples.size()) {
                builder.append("- ... +").append(detectedSecretCount - detectedSecretExamples.size()).append(" more\n");
            }
        } else {
            builder.append("Security: clean\n");
            builder.append("Secrets: no known secret filenames found\n");
        }
        builder.append("Size: ").append(buildSizeWarning(totalBytes));
        return builder.toString().trim();
    }

    private boolean hasScanState() {
        return scanPreviewReady || resultName != null || totalFiles > 0 || skippedItems > 0 || totalBytes > 0L;
    }

    private String buildReadinessText() {
        if (!hasScanState()) {
            return "Readiness: waiting for scan\nMode: " + activeMode.title + "\nNext: run SCAN SOURCE";
        }
        return buildReadinessTextFor(totalFiles, totalBytes, detectedSecretCount, skippedItems, true);
    }

    private String buildReadinessTextFor(int files, long bytes, int secretCount, int skipped, boolean scanned) {
        if (!scanned) {
            return "Readiness: waiting for scan\nMode: " + activeMode.title;
        }
        int score = calculateReadinessScore(files, bytes, secretCount, skipped);
        String level = readinessLevel(score);
        StringBuilder builder = new StringBuilder();
        builder.append("Readiness: ").append(score).append("/100 ").append(level).append('\n');
        builder.append("Mode: ").append(activeMode.title).append('\n');
        builder.append("Files: ").append(files).append(" included / ").append(skipped).append(" skipped\n");
        builder.append("Size: ").append(buildSizeWarning(bytes)).append('\n');
        if (secretCount > 0) {
            builder.append("Security: protected, ").append(secretCount).append(" potential secret files excluded\n");
        } else {
            builder.append("Security: clean, no known secret filenames detected\n");
        }
        builder.append("Advice: ").append(readinessAdvice(score, bytes, secretCount, files));
        return builder.toString().trim();
    }

    private int calculateReadinessScore(int files, long bytes, int secretCount, int skipped) {
        int score = 100;
        if (files <= 0) score -= 45;
        if (bytes >= 200L * 1024L * 1024L) {
            score -= 45;
        } else if (bytes >= 100L * 1024L * 1024L) {
            score -= 25;
        } else if (bytes >= 50L * 1024L * 1024L) {
            score -= 12;
        }
        if (secretCount > 0) score -= 6;
        if (skipped <= 0 && files > 20) score -= 5;
        if (activeMode == ArchiveMode.BACKUP_SAFE) score -= 8;
        if (score < 0) score = 0;
        if (score > 100) score = 100;
        return score;
    }

    private String readinessLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "READY";
        if (score >= 55) return "CHECK";
        return "RISKY";
    }

    private String readinessAdvice(int score, long bytes, int secretCount, int files) {
        if (files <= 0) return "nothing useful will be packaged; check selected folder or clean rules";
        if (bytes >= 200L * 1024L * 1024L) return "package is too large for most AI uploads; split project or use AI AUDIT mode";
        if (bytes >= 100L * 1024L * 1024L) return "large package; consider removing assets, generated docs or vendored sources";
        if (secretCount > 0) return "safe to send only if excluded secrets are expected and not needed for audit";
        if (activeMode == ArchiveMode.BACKUP_SAFE) return "good for backup; AI AUDIT is cleaner for Claude or ChatGPT";
        if (score >= 90) return "ready to upload with the ZIP and Markdown report";
        return "review scan details before sharing";
    }

    private String buildSizeWarning(long sourceBytes) {
        if (sourceBytes >= 200L * 1024L * 1024L) return "BLOCKER: source package may be too large for AI upload (" + formatBytes(sourceBytes) + ")";
        if (sourceBytes >= 100L * 1024L * 1024L) return "STRONG WARNING: source package is large (" + formatBytes(sourceBytes) + ")";
        if (sourceBytes >= 50L * 1024L * 1024L) return "WARNING: source package may be large for AI upload (" + formatBytes(sourceBytes) + ")";
        return "OK: source size is AI-friendly (" + formatBytes(sourceBytes) + ")";
    }

    private void saveHistory(String zipName, String reportName) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String old = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("history", "");
        ArrayList<String> lines = new ArrayList<>();
        lines.add(stamp + " | " + activeMode.title + " | " + zipName + (reportName == null ? "" : " | " + reportName));
        if (old != null && !old.trim().isEmpty()) {
            String[] split = old.split("\n");
            for (String line : split) {
                if (line != null && !line.trim().isEmpty() && lines.size() < 6) lines.add(line);
            }
        }
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) history.append('\n');
            history.append(lines.get(i));
        }
        getSharedPreferences("repoforge_state", MODE_PRIVATE).edit().putString("history", history.toString()).apply();
    }

    private String loadHistoryText() {
        String history = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("history", "");
        if (history == null || history.trim().isEmpty()) {
            return "No packages yet";
        }
        return history;
    }

    private void copyHistory() {
        String history = loadHistoryText();
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("RepoForge history", history));
        appendLog("Copied package history");
        Toast.makeText(this, "History copied", Toast.LENGTH_SHORT).show();
    }

    private void clearHistory() {
        getSharedPreferences("repoforge_state", MODE_PRIVATE).edit().remove("history").apply();
        if (historyView != null) historyView.setText(loadHistoryText());
        appendLog("Package history cleared");
    }

    private void postStatus(String value) {
        mainHandler.post(() -> setStatus(value));
    }

    private void postLog(String value) {
        mainHandler.post(() -> appendLog(value));
    }

    private void postStats(int total, int zipped, int skipped, long sourceBytes, long outputBytes) {
        mainHandler.post(() -> {
            totalFiles = total;
            zippedFiles = zipped;
            skippedItems = skipped;
            totalBytes = sourceBytes;
            writtenBytes = outputBytes;
            renderStats();
        });
    }

    private void setStatus(String value) {
        if (statusView == null) return;
        statusView.setText(value);
        String lower = value == null ? "" : value.toLowerCase(Locale.US);
        if (lower.contains("failed") || lower.contains("error")) {
            statusView.setTextColor(DANGER);
        } else if (lower.contains("warning") || lower.contains("select")) {
            statusView.setTextColor(WARNING);
        } else if (lower.contains("created") || lower.contains("done")) {
            statusView.setTextColor(SUCCESS);
        } else if (busy || lower.contains("scanning") || lower.contains("archiving") || lower.contains("preparing") || lower.contains("finalizing")) {
            statusView.setTextColor(BRAND_CYAN_SOFT);
        } else {
            statusView.setTextColor(TEXT_MAIN);
        }
    }

    private void appendLog(String line) {
        if (line == null) return;
        logLines.add(line);
        while (logLines.size() > 80) logLines.remove(0);
        renderLog();
    }

    private void renderAll() {
        if (modeValueView != null) {
            modeValueView.setText("MODE: " + activeMode.title + "\n" + activeMode.summary);
        }
        if (rulesetView != null) {
            rulesetView.setText(buildRulesetText());
        }
        if (scanDetailsView != null) {
            scanDetailsView.setText(buildScanDetailsText());
        }
        if (readinessView != null) {
            readinessView.setText(buildReadinessText());
            int score = hasScanState() ? calculateReadinessScore(totalFiles, totalBytes, detectedSecretCount, skippedItems) : 0;
            readinessView.setTextColor(!hasScanState() ? TEXT_MUTED : (score >= 75 ? SUCCESS : (score >= 55 ? WARNING : DANGER)));
        }
        if (copyReadinessButton != null) {
            boolean enabled = !busy && hasScanState();
            copyReadinessButton.setEnabled(enabled);
            copyReadinessButton.setAlpha(enabled ? 1.0f : 0.45f);
        }
        if (securityView != null) {
            securityView.setText(buildSecurityText());
            securityView.setTextColor(detectedSecretCount > 0 ? WARNING : TEXT_MUTED);
        }
        if (historyView != null) {
            historyView.setText(loadHistoryText());
        }
        renderModeButtons();
        if (selectedFolderView != null) {
            selectedFolderView.setText(selectedFolderName);
            selectedFolderView.setTextColor(selectedTreeUri == null ? WARNING : TEXT_MAIN);
        }
        if (useLastButton != null) {
            String uriText = getSharedPreferences("repoforge_state", MODE_PRIVATE).getString("tree_uri", null);
            boolean hasSaved = uriText != null && !uriText.trim().isEmpty();
            useLastButton.setEnabled(!busy && hasSaved);
            useLastButton.setAlpha(!busy && hasSaved ? 1.0f : 0.45f);
        }
        if (scanButton != null) {
            scanButton.setEnabled(!busy && selectedTreeUri != null);
            scanButton.setAlpha(!busy && selectedTreeUri != null ? 1.0f : 0.45f);
            scanButton.setText(busy ? "SCANNING" : "SCAN SOURCE");
        }
        if (createButton != null) {
            createButton.setEnabled(!busy && selectedTreeUri != null);
            createButton.setAlpha(!busy && selectedTreeUri != null ? 1.0f : 0.45f);
            createButton.setText(busy ? "BUILDING" : "BUILD PACKAGE");
        }
        if (resultPanel != null) {
            resultPanel.setVisibility(resultName == null ? View.GONE : View.VISIBLE);
        }
        if (resultPathView != null && resultName != null) {
            resultPathView.setText("Downloads/RepoForge/" + resultName);
        }
        if (resultSummaryView != null && resultName != null) {
            StringBuilder summary = new StringBuilder();
            summary.append("Archive: ").append(resultName).append('\n');
            summary.append("Source: ").append(selectedFolderName).append('\n');
            summary.append("Files: ").append(zippedFiles).append('/').append(totalFiles).append(" · skipped ").append(skippedItems).append('\n');
            summary.append("Size: ").append(formatBytes(writtenBytes)).append(" written / ").append(formatBytes(totalBytes)).append(" source");
            if (reportName != null) summary.append("\nReport: ").append(reportName);
            resultSummaryView.setText(summary.toString());
        }
        renderStats();
        renderLog();
    }

    private void renderModeButtons() {
        updateModeButton(modeAiButton, activeMode == ArchiveMode.AI_AUDIT);
        updateModeButton(modeGithubButton, activeMode == ArchiveMode.GITHUB_CLEAN);
        updateModeButton(modeBackupButton, activeMode == ArchiveMode.BACKUP_SAFE);
    }

    private void updateModeButton(TextView button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? TEXT_MAIN : BRAND_CYAN_SOFT);
        button.setBackground(round(selected ? Color.rgb(13, 57, 83) : Color.rgb(8, 15, 24), selected ? BRAND_CYAN : Color.rgb(31, 66, 94), 999));
    }

    private String buildRulesetText() {
        StringBuilder builder = new StringBuilder();
        builder.append("mode     : ").append(activeMode.title).append("\n");
        builder.append("purpose  : ").append(activeMode.summary).append("\n\n");
        for (String rule : DefaultExclusions.describeRules(activeMode)) {
            builder.append("✓ ").append(rule).append("\n");
        }
        return builder.toString().trim();
    }

    private void renderStats() {
        if (fileStatView != null) {
            fileStatView.setText(scanPreviewReady && !busy && resultName == null ? totalFiles + " ready" : zippedFiles + "/" + totalFiles);
        }
        if (skippedStatView != null) skippedStatView.setText(String.valueOf(skippedItems));
        if (sourceStatView != null) sourceStatView.setText(formatBytes(totalBytes));
        if (writtenStatView != null) writtenStatView.setText(formatBytes(writtenBytes));
        if (progressFill != null && progressSpacer != null) {
            float progress = scanPreviewReady && !busy && resultName == null && totalFiles > 0 ? 1.0f : (totalFiles <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, (float) zippedFiles / (float) totalFiles)));
            LinearLayout.LayoutParams fillParams = (LinearLayout.LayoutParams) progressFill.getLayoutParams();
            LinearLayout.LayoutParams spacerParams = (LinearLayout.LayoutParams) progressSpacer.getLayoutParams();
            fillParams.weight = progress;
            spacerParams.weight = 1.0f - progress;
            progressFill.setLayoutParams(fillParams);
            progressSpacer.setLayoutParams(spacerParams);
        }
    }

    private void renderLog() {
        if (logView == null) return;
        if (logLines.isEmpty()) {
            logView.setText("No log entries");
            return;
        }
        int from = Math.max(0, logLines.size() - 8);
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < logLines.size(); i++) {
            builder.append('›').append(' ').append(logLines.get(i));
            if (i != logLines.size() - 1) builder.append('\n');
        }
        logView.setText(builder.toString());
    }

    private LinearLayout panel(String title) {
        LinearLayout card = panelBase(22, false);
        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        header.addView(circle(BRAND_CYAN, 7));
        addGapHorizontal(header, 9);
        header.addView(textMono(title, BRAND_CYAN_SOFT, 12, false));
        card.addView(header);
        addGap(card, 14);
        return card;
    }

    private LinearLayout panelBase(int radiusDp, boolean gradient) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(gradient ? gradientRound(radiusDp) : round(CARD_BG, STROKE, radiusDp));
        card.setClipToOutline(false);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private LinearLayout row(int gravity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(gravity);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private TextView statBox(LinearLayout parent, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(13), dp(13), dp(13));
        box.setBackground(round(CARD_BG_2, STROKE, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(params);

        TextView labelView = textMono(label, TEXT_MUTED, 10, false);
        TextView valueView = textBody(value, TEXT_MAIN, 15);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(labelView);
        addGap(box, 6);
        box.addView(valueView);
        parent.addView(box);
        return valueView;
    }

    private TextView button(String label, boolean primary) {
        TextView button = textMono(label, primary ? TEXT_MAIN : BRAND_CYAN_SOFT, 11, false);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(dp(16), dp(11), dp(16), dp(11));
        button.setBackground(round(primary ? Color.rgb(13, 57, 83) : Color.rgb(8, 15, 24), primary ? BRAND_CYAN : Color.rgb(31, 66, 94), 999));
        button.setMinWidth(dp(112));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView chip(String label) {
        TextView chip = textMono(label, TEXT_MUTED, 10, false);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(round(Color.rgb(8, 15, 24), Color.rgb(31, 55, 78), 999));
        return chip;
    }

    private TextView textTitle(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(TEXT_MAIN);
        text.setTextSize(34);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setIncludeFontPadding(true);
        return text;
    }

    private TextView textBody(String value, int color, int sp) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        text.setLineSpacing(dp(2), 1.0f);
        text.setIncludeFontPadding(true);
        return text;
    }

    private TextView textMono(String value, int color, int sp, boolean multiLine) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        text.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        text.setIncludeFontPadding(true);
        text.setSingleLine(!multiLine);
        if (!multiLine) text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private View circle(int color, int sizeDp) {
        View view = new View(this);
        view.setBackground(round(color, color, 999));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable gradientRound(int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(9, 19, 32), Color.rgb(10, 31, 49), BASE_BG}
        );
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), STROKE);
        return drawable;
    }

    private void addGap(LinearLayout parent, int dp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void addGapHorizontal(LinearLayout parent, int dp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(dp(dp), 1));
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String sanitizeFileName(String raw) {
        String value = raw == null ? "project" : raw.trim();
        if (value.isEmpty()) value = "project";
        value = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        while (value.startsWith("_")) value = value.substring(1);
        while (value.endsWith("_")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) value = "project";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message;
    }

    private static String formatBytes(long value) {
        if (value <= 0L) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        double size = value;
        int unit = 0;
        while (size >= 1024.0 && unit < units.length - 1) {
            size /= 1024.0;
            unit++;
        }
        if (unit == 0) return value + " " + units[unit];
        return String.format(Locale.US, "%.1f %s", size, units[unit]);
    }

    private enum ArchiveMode {
        AI_AUDIT("AI AUDIT", "ai_audit", "Cleanest package for Claude / ChatGPT audit"),
        GITHUB_CLEAN("GITHUB CLEAN", "github_clean", "Source package for repository review without generated trash"),
        BACKUP_SAFE("BACKUP SAFE", "backup_safe", "Broader backup, but secrets and dangerous local files stay excluded");

        final String title;
        final String slug;
        final String summary;

        ArchiveMode(String title, String slug, String summary) {
            this.title = title;
            this.slug = slug;
            this.summary = summary;
        }

        static ArchiveMode fromName(String value) {
            if (value == null) return AI_AUDIT;
            for (ArchiveMode mode : values()) {
                if (mode.name().equals(value)) return mode;
            }
            return AI_AUDIT;
        }
    }

    private static final class DefaultExclusions {
        private static final Set<String> ALWAYS_SKIPPED_DIRECTORIES = new HashSet<>(Arrays.asList(
                ".gradle", ".kotlin", ".cxx", "build", "captures", "out", "node_modules", "target"
        ));
        private static final Set<String> AI_AND_GITHUB_ONLY_DIRECTORIES = new HashSet<>(Arrays.asList(
                ".git", ".idea", ".vscode"
        ));
        private static final Set<String> SKIPPED_EXACT_FILES = new HashSet<>(Arrays.asList(
                ".ds_store", "debug.keystore", "keystore.properties", "local.properties", "cpu_snapshot", "last_apply",
                "auto_apply_events", "auto_apply_cooldown", "auto_mode", ".env", ".env.local", ".env.production",
                "secrets.properties", "secret.properties", "google-services.json", "service-account.json",
                "play-service-account.json", "firebase-adminsdk.json", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
                "known_hosts", "gradle.properties.local"
        ));
        private static final String[] ALWAYS_SKIPPED_EXTENSIONS = new String[]{
                ".apk", ".aab", ".ap_", ".idsig", ".zip", ".tar", ".tgz", ".gz", ".7z", ".rar",
                ".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".crt", ".cer",
                ".der", ".csr", ".gpg", ".asc", ".mobileprovision"
        };
        private static final String[] AI_AND_GITHUB_SKIPPED_EXTENSIONS = new String[]{
                ".log", ".tmp", ".bak", ".swp", ".swo"
        };

        static List<String> describeRules(ArchiveMode mode) {
            ArrayList<String> rules = new ArrayList<>();
            rules.add("always skip secrets: local.properties, .env, secrets.properties, google-services.json, service-account.json");
            rules.add("always skip signing material: *.jks, *.keystore, *.pem, *.key, *.p12, *.pfx, *.crt, *.cer, *.gpg, *.asc");
            rules.add("always skip packages/archives: *.apk, *.aab, *.ap_, *.idsig, *.zip, *.tar, *.tgz, *.gz, *.7z, *.rar");
            rules.add("always skip generated heavy dirs: build, .gradle, .kotlin, .cxx, out, target, node_modules");
            if (mode == ArchiveMode.AI_AUDIT) {
                rules.add("AI AUDIT skips IDE/VCS state: .git, .idea, .vscode");
                rules.add("AI AUDIT skips logs/temp/backups: *.log, *.tmp, *.bak, *.swp, *.swo");
            } else if (mode == ArchiveMode.GITHUB_CLEAN) {
                rules.add("GITHUB CLEAN skips IDE/VCS state: .git, .idea, .vscode");
                rules.add("GITHUB CLEAN skips logs/temp/backups: *.log, *.tmp, *.bak, *.swp, *.swo");
            } else {
                rules.add("BACKUP SAFE keeps more metadata: .git, .idea and .vscode are allowed when present");
                rules.add("BACKUP SAFE keeps logs/temp files unless they are archives or secrets");
            }
            return rules;
        }

        static boolean isSecuritySensitive(String name, String relativePath, boolean directory) {
            if (directory) return false;
            String lowerName = name.toLowerCase(Locale.US);
            String lowerPath = relativePath.replace('\\', '/').toLowerCase(Locale.US);
            if (SKIPPED_EXACT_FILES.contains(lowerName)) return true;
            String[] secretExtensions = new String[]{".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".crt", ".cer", ".der", ".csr", ".gpg", ".asc", ".mobileprovision"};
            for (String ext : secretExtensions) {
                if (lowerName.endsWith(ext)) return true;
            }
            return lowerPath.contains("/secrets/") || lowerPath.contains("/secret/") || lowerPath.contains("/credentials/");
        }

        static boolean shouldSkip(String name, String relativePath, boolean directory, ArchiveMode mode) {
            String lowerName = name.toLowerCase(Locale.US);
            String lowerPath = relativePath.replace('\\', '/').toLowerCase(Locale.US);
            if (directory) {
                if (ALWAYS_SKIPPED_DIRECTORIES.contains(lowerName)) return true;
                if (mode != ArchiveMode.BACKUP_SAFE && AI_AND_GITHUB_ONLY_DIRECTORIES.contains(lowerName)) return true;
            }
            if (!directory && SKIPPED_EXACT_FILES.contains(lowerName)) return true;
            if (!directory) {
                for (String ext : ALWAYS_SKIPPED_EXTENSIONS) {
                    if (lowerName.endsWith(ext)) return true;
                }
                if (mode != ArchiveMode.BACKUP_SAFE) {
                    for (String ext : AI_AND_GITHUB_SKIPPED_EXTENSIONS) {
                        if (lowerName.endsWith(ext)) return true;
                    }
                }
            }
            if (lowerPath.contains("/build/") || lowerPath.contains("/.gradle/") || lowerPath.contains("/.kotlin/") || lowerPath.contains("/.cxx/") || lowerPath.contains("/node_modules/") || lowerPath.contains("/target/")) return true;
            if (mode != ArchiveMode.BACKUP_SAFE && (lowerPath.contains("/.idea/") || lowerPath.contains("/.git/") || lowerPath.contains("/.vscode/"))) return true;
            return false;
        }
    }

    private static final class ArchiveEntry {
        final Uri uri;
        final String relativePath;
        final long size;

        ArchiveEntry(Uri uri, String relativePath, long size) {
            this.uri = uri;
            this.relativePath = relativePath;
            this.size = size;
        }
    }

    private static final class MutableStats {
        int skippedCount;
        int filesCount;
        int secretCount;
        long totalBytes;
        final ArrayList<String> secretExamples = new ArrayList<>();
        final ArrayList<String> skippedExamples = new ArrayList<>();
    }

    private static final class ScanResult {
        final ArrayList<ArchiveEntry> entries;
        final int skippedCount;
        final int filesCount;
        final long totalBytes;
        final int secretCount;
        final ArrayList<String> secretExamples;
        final ArrayList<String> skippedExamples;
        final ArrayList<String> includedExamples;

        ScanResult(ArrayList<ArchiveEntry> entries, int skippedCount, int filesCount, long totalBytes, int secretCount, ArrayList<String> secretExamples, ArrayList<String> skippedExamples, ArrayList<String> includedExamples) {
            this.entries = entries;
            this.skippedCount = skippedCount;
            this.filesCount = filesCount;
            this.totalBytes = totalBytes;
            this.secretCount = secretCount;
            this.secretExamples = new ArrayList<>(secretExamples);
            this.skippedExamples = new ArrayList<>(skippedExamples);
            this.includedExamples = new ArrayList<>(includedExamples);
        }
    }

    private static final class ZipBuildResult {
        final Uri uri;
        final String displayName;
        final Uri reportUri;
        final String reportName;
        final ScanResult scanResult;

        ZipBuildResult(Uri uri, String displayName) {
            this(uri, displayName, null, null, null);
        }

        ZipBuildResult(Uri uri, String displayName, Uri reportUri, String reportName, ScanResult scanResult) {
            this.uri = uri;
            this.displayName = displayName;
            this.reportUri = reportUri;
            this.reportName = reportName;
            this.scanResult = scanResult;
        }
    }
}
