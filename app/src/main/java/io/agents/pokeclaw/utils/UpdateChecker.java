// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

/**
 * Checks GitHub Releases for a newer version of PokeClaw.
 * Call checkForUpdate() once in onCreate — it runs on a background thread,
 * shows a dialog on the main thread if a newer version exists.
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/Altamimi79/Sanad-Claw/releases/latest";
    private static final String GITHUB_API_ALL = "https://api.github.com/repos/Altamimi79/Sanad-Claw/releases?per_page=20";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000; // Once per day

    /** Automatic check: at most once per day. Shows a dialog only when a newer release exists. */
    public static void checkForUpdate(Activity activity) {
        long lastCheck = io.agents.pokeclaw.utils.KVUtils.INSTANCE.getLong("last_update_check", 0);
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            XLog.d(TAG, "Skipping update check, last check was " + ((now - lastCheck) / 1000 / 60) + " min ago");
            return;
        }
        runCheck(activity, true);
    }

    /**
     * Manual check from the About screen. Ignores the daily limit. Always reports a result:
     * a newer version dialog, an "up to date" message, or a network error message.
     */
    public static void checkNow(Activity activity) {
        runCheck(activity, false);
    }

    private static void runCheck(Activity activity, boolean automatic) {
        boolean debugBuild = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        XLog.d(TAG, "Checking for updates (automatic=" + automatic + ") on " + (debugBuild ? "debug" : "release") + " build");

        final long now = System.currentTimeMillis();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String currentVersion = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;

                // The user picks the channel in Settings. Stable only looks at stable
                // releases; beta also looks at pre-releases (dev/rc).
                boolean betaChannel = io.agents.pokeclaw.utils.KVUtils.INSTANCE.UPDATE_CHANNEL_BETA
                        .equals(io.agents.pokeclaw.utils.KVUtils.INSTANCE.getUpdateChannel());
                String apiUrl = betaChannel ? GITHUB_API_ALL : GITHUB_API_LATEST;

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    XLog.w(TAG, "GitHub API returned " + conn.getResponseCode());
                    reportFailure(activity, automatic, "GitHub responded with " + conn.getResponseCode());
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject release;
                String latestTag;
                if (betaChannel) {
                    JSONArray releases = new JSONArray(sb.toString());
                    JSONObject best = null;
                    String bestTag = null;
                    for (int i = 0; i < releases.length(); i++) {
                        JSONObject candidate = releases.getJSONObject(i);
                        if (candidate.optBoolean("draft", false)) continue;
                        String tag = candidate.getString("tag_name").replaceFirst("^v", "");
                        if (best == null || isNewer(tag, bestTag)) {
                            best = candidate;
                            bestTag = tag;
                        }
                    }
                    if (best == null) {
                        reportFailure(activity, automatic, "No releases found.");
                        return;
                    }
                    release = best;
                    latestTag = bestTag;
                } else {
                    release = new JSONObject(sb.toString());
                    latestTag = release.getString("tag_name").replaceFirst("^v", "");
                }
                String downloadUrl = release.getString("html_url");
                String notes = release.optString("body", "").trim();
                // Prefer the APK asset so the Update button can download + install in-app.
                String apkUrl = "";
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        if (asset.optString("name", "").endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }

                XLog.i(TAG, "Current: " + currentVersion + ", Latest: " + latestTag);

                // Save check time
                io.agents.pokeclaw.utils.KVUtils.INSTANCE.putLong("last_update_check", now);

                if (isNewer(latestTag, currentVersion)) {
                    String ignored = io.agents.pokeclaw.utils.KVUtils.INSTANCE
                            .getString("ignored_update_version", "");
                    if (automatic && latestTag.equals(ignored)) {
                        XLog.i(TAG, "Skipping ignored version " + latestTag);
                        return;
                    }
                    final String finalApkUrl = apkUrl;
                    activity.runOnUiThread(() -> showUpdateDialog(activity, latestTag, downloadUrl, finalApkUrl, notes, debugBuild));
                } else if (!automatic) {
                    activity.runOnUiThread(() -> showUpToDate(activity, currentVersion));
                }

            } catch (Exception e) {
                XLog.w(TAG, "Update check failed", e);
                reportFailure(activity, automatic, "Could not check for updates. Check your internet connection.");
            }
        });
    }

    private static void reportFailure(Activity activity, boolean automatic, String reason) {
        if (automatic) return; // automatic checks stay silent; the user asked for nothing
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle(activity.getString(io.agents.pokeclaw.R.string.update_failed_title))
                .setMessage(reason)
                .setPositiveButton(activity.getString(io.agents.pokeclaw.R.string.update_ok), null)
                .show());
    }

    private static void showUpToDate(Activity activity, String currentVersion) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(io.agents.pokeclaw.R.string.update_up_to_date_title))
                .setMessage(activity.getString(io.agents.pokeclaw.R.string.update_up_to_date_message, currentVersion))
                .setPositiveButton(activity.getString(io.agents.pokeclaw.R.string.update_ok), null)
                .show();
    }

    /**
     * Compare versions. Returns true if remote > local.
     *
     * Pre-release suffixes ("-dev-5", "-rc1", "-beta2") are stripped before comparing
     * the numeric parts. When the base versions are equal:
     *  - stable remote beats a pre-release local,
     *  - two pre-releases are compared by their trailing number (dev-8 > dev-7).
     */
    static boolean isNewer(String remote, String local) {
        String remoteBase = stripPrerelease(remote);
        String localBase = stripPrerelease(local);
        int cmp = compareNumeric(remoteBase, localBase);
        if (cmp != 0) return cmp > 0;

        boolean remotePre = isPrerelease(remote);
        boolean localPre = isPrerelease(local);
        if (!remotePre && localPre) return true;
        if (remotePre && !localPre) return false;
        if (remotePre && localPre) {
            return prereleaseNumber(remote) > prereleaseNumber(local);
        }
        return false;
    }

    /** Trailing build number of a pre-release suffix: "1.0.3-dev-8" -> 8, "-rc2" -> 2. */
    private static int prereleaseNumber(String version) {
        if (version == null) return 0;
        String suffix = version.replaceFirst("^[^-+]*[-+]", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(suffix);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String stripPrerelease(String version) {
        if (version == null) return "";
        return version.replaceFirst("[-+].*$", "").trim();
    }

    private static boolean isPrerelease(String version) {
        return version != null && version.matches(".*[-+].*");
    }

    private static int compareNumeric(String remote, String local) {
        try {
            String[] r = remote.split("\\.");
            String[] l = local.split("\\.");
            for (int i = 0; i < Math.max(r.length, l.length); i++) {
                int rv = i < r.length && !r[i].isEmpty() ? Integer.parseInt(r[i]) : 0;
                int lv = i < l.length && !l[i].isEmpty() ? Integer.parseInt(l[i]) : 0;
                if (rv > lv) return 1;
                if (rv < lv) return -1;
            }
        } catch (NumberFormatException e) {
            XLog.w(TAG, "Version parse error: remote=" + remote + " local=" + local);
        }
        return 0;
    }

    private static void showUpdateDialog(Activity activity, String version, String pageUrl, String apkUrl, String notes, boolean debugBuild) {
        try {
            String notesText = notes.isEmpty()
                    ? activity.getString(io.agents.pokeclaw.R.string.update_no_notes)
                    : notes;
            String message = activity.getString(io.agents.pokeclaw.R.string.update_available_message, version, notesText);
            if (debugBuild) {
                message += "\n\n" + activity.getString(io.agents.pokeclaw.R.string.update_debug_note);
            }
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(io.agents.pokeclaw.R.string.update_available_title))
                    .setMessage(message)
                    .setPositiveButton(activity.getString(io.agents.pokeclaw.R.string.update_now), (d, w) -> {
                        if (apkUrl != null && !apkUrl.isEmpty()) {
                            downloadAndInstall(activity, apkUrl, pageUrl);
                        } else {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)));
                        }
                    })
                    .setNeutralButton(activity.getString(io.agents.pokeclaw.R.string.update_ignore), (d, w) -> {
                        io.agents.pokeclaw.utils.KVUtils.INSTANCE.putString("ignored_update_version", version);
                        XLog.i(TAG, "User ignored update version " + version);
                    })
                    .setNegativeButton(activity.getString(io.agents.pokeclaw.R.string.update_later), null)
                    .show();
            // Match the dialog buttons to the active theme accent.
            int accent = io.agents.pokeclaw.ui.chat.ThemeManager.INSTANCE.getColors().getSendColor();
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent);
            }
        } catch (Exception e) {
            XLog.w(TAG, "Failed to show update dialog", e);
        }
    }

    /** Download the release APK and launch the system installer. */
    private static void downloadAndInstall(Activity activity, String apkUrl, String fallbackPageUrl) {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(activity);
        progress.setMessage(activity.getString(io.agents.pokeclaw.R.string.update_downloading));
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(activity.getExternalFilesDir(null), "Download");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, "sanad-update.apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        activity, activity.getPackageName() + ".fileprovider", out);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    activity.startActivity(intent);
                });
            } catch (Exception e) {
                XLog.w(TAG, "APK download failed", e);
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(activity)
                            .setTitle(activity.getString(io.agents.pokeclaw.R.string.update_failed_title))
                            .setMessage(activity.getString(io.agents.pokeclaw.R.string.update_download_failed))
                            .setPositiveButton(activity.getString(io.agents.pokeclaw.R.string.update_ok), null)
                            .show();
                    // Offer the release page as a fallback.
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackPageUrl)));
                    } catch (Exception ignored) {
                    }
                });
            }
        }).start();
    }
}
