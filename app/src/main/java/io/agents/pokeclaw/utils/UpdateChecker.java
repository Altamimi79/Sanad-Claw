// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.net.Uri;

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
    private static final String GITHUB_API = "https://api.github.com/repos/Altamimi79/Sanad-Claw/releases/latest";
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

                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API).openConnection();
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

                JSONObject release = new JSONObject(sb.toString());
                String latestTag = release.getString("tag_name").replaceFirst("^v", "");
                String downloadUrl = release.getString("html_url");
                String notes = release.optString("body", "").trim();

                XLog.i(TAG, "Current: " + currentVersion + ", Latest: " + latestTag);

                // Save check time
                io.agents.pokeclaw.utils.KVUtils.INSTANCE.putLong("last_update_check", now);

                if (isNewer(latestTag, currentVersion)) {
                    activity.runOnUiThread(() -> showUpdateDialog(activity, latestTag, downloadUrl, notes, debugBuild));
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
                .setTitle("Update check failed")
                .setMessage(reason)
                .setPositiveButton("OK", null)
                .show());
    }

    private static void showUpToDate(Activity activity, String currentVersion) {
        new AlertDialog.Builder(activity)
                .setTitle("No update available")
                .setMessage("You are running the latest version (v" + currentVersion + ").")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Compare versions. Returns true if remote > local.
     *
     * Pre-release suffixes ("-dev-5", "-rc1", "-beta2") are stripped before comparing
     * the numeric parts, so "1.0.3" vs "1.0.3-dev-5" still compares correctly. When the
     * numeric parts are equal, a stable remote release beats a pre-release local build.
     */
    static boolean isNewer(String remote, String local) {
        String remoteBase = stripPrerelease(remote);
        String localBase = stripPrerelease(local);
        int cmp = compareNumeric(remoteBase, localBase);
        if (cmp != 0) return cmp > 0;
        return !isPrerelease(remote) && isPrerelease(local);
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

    private static void showUpdateDialog(Activity activity, String version, String url, String notes, boolean debugBuild) {
        try {
            StringBuilder message = new StringBuilder()
                    .append("New version: v")
                    .append(version)
                    .append("\n\nWhat's changed:\n")
                    .append(notes.isEmpty() ? "No release notes were provided." : notes)
                    .append("\n\nDo you want to update now?");
            if (debugBuild) {
                message.append("\n\nThis build is debuggable. If Android blocks the install, uninstall the old debug build first, then install the new APK.");
            }
            new AlertDialog.Builder(activity)
                    .setTitle("Update available")
                    .setMessage(message.toString())
                    .setPositiveButton("Update", (d, w) -> {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    })
                    .setNegativeButton("Later", null)
                    .show();
        } catch (Exception e) {
            XLog.w(TAG, "Failed to show update dialog", e);
        }
    }
}
