package com.jima.ai.core.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.jima.ai.core.assistant.JimaStateManager;

import java.util.List;
import java.util.Locale;

public class JimaAccessibilityService extends AccessibilityService {

    private static volatile JimaAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public static JimaAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 0% Battery Guard: Immediately return if Jima is sleeping
        if (!JimaStateManager.getInstance().isAwake()) {
            return;
        }
        if (event != null && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            JimaStateManager.getInstance().setActiveTargetPackage(pkg);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /**
     * Dumps visible interactive UI elements so Gemini Brain knows what is on screen.
     */
    public String dumpScreenContext() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "Screen is empty or locked.";
        }
        StringBuilder sb = new StringBuilder();
        CharSequence pkg = root.getPackageName();
        sb.append("ActiveApp: ").append(pkg != null ? pkg : "unknown").append("\nElements:\n");
        traverseNode(root, sb, 0);
        root.recycle();
        String result = sb.toString();
        return result.length() > 2500 ? result.substring(0, 2500) : result;
    }

    private void traverseNode(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 14 || sb.length() > 2500) {
            return;
        }

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String viewId = node.getViewIdResourceName();

        boolean hasLabel = (text != null && text.length() > 0) || (desc != null && desc.length() > 0);
        if (hasLabel || node.isEditable()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            sb.append("- [");
            if (text != null && text.length() > 0) {
                sb.append("text='").append(text).append("' ");
            }
            if (desc != null && desc.length() > 0) {
                sb.append("desc='").append(desc).append("' ");
            }
            if (viewId != null && viewId.contains("/")) {
                sb.append("id='").append(viewId.substring(viewId.indexOf('/') + 1)).append("' ");
            }
            if (node.isEditable()) {
                sb.append("EDITABLE ");
            }
            if (node.isClickable()) {
                sb.append("CLICKABLE ");
            }
            sb.append("at(").append(bounds.centerX()).append(",").append(bounds.centerY()).append(")]\n");
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child, sb, depth + 1);
                child.recycle();
            }
        }
    }

    /**
     * Finds a button/element by visible text, content description, or view ID and clicks it.
     */
    public boolean clickByTextOrDescription(String targetQuery) {
        if (targetQuery == null || targetQuery.trim().isEmpty()) {
            return false;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        String queryLower = targetQuery.toLowerCase(Locale.ROOT).trim();
        AccessibilityNodeInfo matched = findMatchingNode(root, queryLower);
        boolean success = false;

        if (matched != null) {
            success = performClickOrTap(matched);
            matched.recycle();
        }
        root.recycle();
        return success;
    }

    private AccessibilityNodeInfo findMatchingNode(AccessibilityNodeInfo node, String queryLower) {
        if (node == null) return null;

        String text = node.getText() != null ? node.getText().toString().toLowerCase(Locale.ROOT) : "";
        String desc = node.getContentDescription() != null
                ? node.getContentDescription().toString().toLowerCase(Locale.ROOT) : "";
        String viewId = node.getViewIdResourceName() != null
                ? node.getViewIdResourceName().toLowerCase(Locale.ROOT) : "";

        if (text.contains(queryLower) || desc.contains(queryLower) || viewId.contains(queryLower)) {
            return AccessibilityNodeInfo.obtain(node);
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findMatchingNode(child, queryLower);
                child.recycle();
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean performClickOrTap(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        while (current != null) {
            if (current.isClickable()) {
                boolean clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                current.recycle();
                if (clicked) return true;
                break;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }

        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (!rect.isEmpty()) {
            return clickAtCoordinates(rect.centerX(), rect.centerY());
        }
        return false;
    }

    /**
     * Taps exact screen coordinates (x, y).
     */
    public boolean clickAtCoordinates(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 65));
            return dispatchGesture(builder.build(), null, null);
        }
        return false;
    }

    /**
     * Types text into currently focused input or first editable text box on screen.
     */
    public boolean typeIntoField(String targetHint, String textToType) {
        if (textToType == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo targetEdit = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (targetEdit == null || !targetEdit.isEditable()) {
            if (targetEdit != null) targetEdit.recycle();
            targetEdit = findFirstEditableNode(root, targetHint != null ? targetHint.toLowerCase(Locale.ROOT) : "");
        }

        boolean success = false;
        if (targetEdit != null) {
            performClickOrTap(targetEdit);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            success = targetEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                targetEdit.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            }
            targetEdit.recycle();
        }
        root.recycle();
        return success;
    }

    private AccessibilityNodeInfo findFirstEditableNode(AccessibilityNodeInfo node, String hintQuery) {
        if (node == null) return null;
        if (node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findFirstEditableNode(child, hintQuery);
                child.recycle();
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Smoothly scrolls the screen UP, DOWN, LEFT, or RIGHT.
     */
    public boolean scrollScreen(String direction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int w = metrics.widthPixels;
        int h = metrics.heightPixels;

        float startX = w * 0.5f;
        float startY = h * 0.72f;
        float endX = w * 0.5f;
        float endY = h * 0.25f;

        String dir = direction != null ? direction.toUpperCase(Locale.ROOT) : "DOWN";
        if (dir.contains("UP") || dir.contains("PREVIOUS")) {
            startY = h * 0.28f;
            endY = h * 0.75f;
        } else if (dir.contains("LEFT")) {
            startX = w * 0.82f;
            endX = w * 0.18f;
            startY = h * 0.5f;
            endY = h * 0.5f;
        } else if (dir.contains("RIGHT")) {
            startX = w * 0.18f;
            endX = w * 0.82f;
            startY = h * 0.5f;
            endY = h * 0.5f;
        }

        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 320));
        return dispatchGesture(builder.build(), null, null);
    }

    /**
     * Launches any installed app by app name (e.g., "YouTube", "Instagram") or package name.
     */
    public boolean launchAppByNameOrPackage(String appQuery) {
        if (appQuery == null || appQuery.trim().isEmpty()) return false;
        String q = appQuery.toLowerCase(Locale.ROOT).trim();

        PackageManager pm = getPackageManager();
        // Fast check for popular apps
        if (q.contains("youtube")) {
            if (launchPackage(pm, "com.google.android.youtube")) return true;
        } else if (q.contains("instagram") || q.contains("insta")) {
            if (launchPackage(pm, "com.instagram.android")) return true;
        } else if (q.contains("whatsapp")) {
            if (launchPackage(pm, "com.whatsapp")) return true;
        } else if (q.contains("chrome") || q.contains("browser")) {
            if (launchPackage(pm, "com.android.chrome")) return true;
        }

        if (launchPackage(pm, appQuery.trim())) {
            return true;
        }

        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            CharSequence label = pm.getApplicationLabel(app);
            String appName = label != null ? label.toString().toLowerCase(Locale.ROOT) : "";
            if (appName.contains(q) || app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                if (launchPackage(pm, app.packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean launchPackage(PackageManager pm, String packageName) {
        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
            return true;
        }
        return false;
    }

    public boolean pressBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean pressHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }
}

