// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool.impl;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import io.agents.pokeclaw.service.ClawAccessibilityService;
import io.agents.pokeclaw.tool.BaseTool;
import io.agents.pokeclaw.tool.ToolParameter;
import io.agents.pokeclaw.tool.ToolResult;
import io.agents.pokeclaw.utils.ContactListUiUtils;
import io.agents.pokeclaw.utils.XLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Sends a file or photo to a contact in a messaging app (WhatsApp by default).
 *
 * Flow: open the app, open the contact's chat, tap Attach, tap Document (files) or Gallery
 * (photos), then choose the file. The send itself is left to the chat's own send control.
 *
 * This tool is classified as SEND_MESSAGE in RiskClassifier, so the user is asked to confirm
 * before it runs (the same switch that guards send_message).
 */
public class SendMediaTool extends BaseTool {

    private static final String TAG = "SendMediaTool";

    @Override
    public String getName() { return "send_media"; }

    @Override
    public String getDisplayName() { return "Send Media"; }

    @Override
    public String getDescriptionEN() {
        return "Send a file or photo from the device to a contact in a messaging app (WhatsApp by default). Requires an absolute file_path.";
    }

    @Override
    public String getDescriptionCN() {
        return getDescriptionEN();
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("contact", "string", "Contact name to send to (e.g. 'Mom')", true),
                new ToolParameter("file_path", "string", "Absolute path of the file or photo to send", true),
                new ToolParameter("app", "string", "Messaging app name (default: WhatsApp)", false)
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        ClawAccessibilityService service = requireAccessibilityService();
        if (service == null) {
            return ToolResult.error("Accessibility service is not running");
        }

        String contact = requireString(params, "contact");
        String filePath = requireString(params, "file_path");
        String app = params.containsKey("app") ? params.get("app").toString() : "WhatsApp";

        File file = new File(filePath);
        if (!file.isFile() || !file.canRead()) {
            return ToolResult.error("File not found or not readable: " + filePath);
        }

        XLog.i(TAG, "Sending '" + file.getName() + "' to " + contact + " via " + app);

        try {
            String packageName = OpenAppTool.resolveAppNameStatic(app);
            if (packageName == null) packageName = app;
            if (!service.openApp(packageName)) {
                return ToolResult.error("Failed to open " + app + ". Is it installed?");
            }
            Thread.sleep(2000);

            if (!ContactListUiUtils.prepareForContactLookup(service, packageName, 4, 1200)) {
                return ToolResult.error("Could not reach a searchable " + app + " chat list.");
            }
            if (!tapContact(service, contact)) {
                return ToolResult.error("Could not find '" + contact + "' in " + app + " chat list.");
            }
            Thread.sleep(3000);

            if (!tapByDescription(service, "Attach")) {
                return ToolResult.error("Could not find the attach button in " + app + ".");
            }
            Thread.sleep(1200);

            // Documents use the "Document" entry; that picker shows files from storage.
            if (!tapByText(service, "Document")) {
                return ToolResult.error("Could not find the Document option in " + app + ".");
            }
            Thread.sleep(2000);

            // The picker shows recent files. Pick the one whose name matches the file.
            if (!tapByText(service, file.getName())) {
                return ToolResult.error("File '" + file.getName() + "' was not found in the " + app + " file picker.");
            }
            Thread.sleep(1500);

            XLog.i(TAG, "Attached '" + file.getName() + "' for " + contact);
            return ToolResult.success("Attached '" + file.getName() + "' for " + contact + " in " + app + ". Confirm the send in the app.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Send media interrupted");
        } catch (Exception e) {
            XLog.e(TAG, "send_media failed", e);
            return ToolResult.error("Send media failed: " + e.getMessage());
        }
    }

    /** Taps the first clickable node whose text equals [label] (exact, case-insensitive). */
    private boolean tapByText(ClawAccessibilityService service, String label) throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> hits = new ArrayList<>();
        collectByText(root, label, hits);
        return tapFirst(hits);
    }

    /** Taps the first clickable node whose content description equals [label]. */
    private boolean tapByDescription(ClawAccessibilityService service, String label) throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> hits = new ArrayList<>();
        collectByDescription(root, label, hits);
        return tapFirst(hits);
    }

    /** Opens the chat with [contact] using the same lookup as send_message. */
    private boolean tapContact(ClawAccessibilityService service, String contact) throws InterruptedException {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> hits = new ArrayList<>();
        collectByText(root, contact, hits);
        return tapFirst(hits);
    }

    private boolean tapFirst(List<AccessibilityNodeInfo> hits) throws InterruptedException {
        for (AccessibilityNodeInfo node : hits) {
            AccessibilityNodeInfo target = clickableAncestor(node);
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 5; i++) {
            if (current.isClickable()) return current;
            current = current.getParent();
        }
        return null;
    }

    private void collectByText(AccessibilityNodeInfo node, String label, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.toString().trim().equalsIgnoreCase(label.trim())) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectByText(node.getChild(i), label, out);
        }
    }

    private void collectByDescription(AccessibilityNodeInfo node, String label, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().trim().equalsIgnoreCase(label.trim())) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectByDescription(node.getChild(i), label, out);
        }
    }
}
