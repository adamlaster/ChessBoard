package com.lastersoft.godotwebview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Custom root container for ChessBoard that detects bezel edge swipe gestures:
 * 1. Top Bezel Swipe (downward): Opens the Fullscreen Launcher.
 * 2. Right Bezel Swipe (leftward): Opens the Browser Controls rail.
 * 3. Swipe Up (when Launcher is open): Dismisses the Launcher.
 * 4. Swipe Right (when Browser Controls rail is open): Dismisses Browser Controls.
 *
 * Implements generous edge-zone thresholding (64dp), velocity/fling detection,
 * multi-touch pinch safety, and Board OS top-right cutout protection to ensure
 * zero interference with normal chessboard play, piece dragging, or Board hardware buttons.
 */
public class BezelGestureFrameLayout extends FrameLayout {

    private static final String TAG = "GodotWebView.Bezel";

    public interface BezelSwipeListener {
        void onTopBezelSwipe();
        void onRightBezelSwipe();
        void onSwipeUpToCloseLauncher();
        void onSwipeRightToCloseControls();
        boolean isLauncherOpen();
        boolean isControlsOpen();
    }

    private static final float BEZEL_ZONE_DP = 64.0f;       // Generous edge touch zone depth (64dp)
    private static final float SWIPE_THRESHOLD_DP = 36.0f;  // Snappy minimum swipe travel distance (36dp)
    private static final float BOARD_CUTOUT_DP = 106.0f;    // Board OS system menu button clearance
    private static final float FLING_VELOCITY_DP = 450.0f;  // Fling velocity threshold (450dp/s)

    private BezelSwipeListener listener;
    private float density = 1.0f;
    private int touchSlop;
    private VelocityTracker velocityTracker;

    private float downX;
    private float downY;
    private boolean trackingTopBezel = false;
    private boolean trackingRightBezel = false;
    private boolean trackingLauncherClose = false;
    private boolean trackingControlsClose = false;
    private boolean gestureIntercepted = false;

    public BezelGestureFrameLayout(Context context) {
        super(context);
        init(context);
    }

    public BezelGestureFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BezelGestureFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setBezelSwipeListener(BezelSwipeListener listener) {
        this.listener = listener;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // If an edge or close gesture is actively being tracked, do not allow child views
        // (e.g. WebView scroll / card row scroll) to disallow interception.
        if (trackingTopBezel || trackingRightBezel || trackingLauncherClose || trackingControlsClose) {
            return;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getX();
        float y = ev.getY();
        int width = getWidth();
        int height = getHeight();

        float bezelZonePx = BEZEL_ZONE_DP * density;
        float swipeThresholdPx = SWIPE_THRESHOLD_DP * density;
        float cutoutPx = BOARD_CUTOUT_DP * density;
        float flingVelocityPx = FLING_VELOCITY_DP * density;

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                gestureIntercepted = false;

                boolean launcherOpen = (listener != null && listener.isLauncherOpen());
                boolean controlsOpen = (listener != null && listener.isControlsOpen());

                // 1. Check Top Bezel: touch starts near top edge, excluding the Board OS cutout at top-right
                boolean inTopZone = (y <= bezelZonePx) && (x < width - cutoutPx);
                // 2. Check Right Bezel: touch starts near right edge, excluding the Board OS cutout at top-right
                boolean inRightZone = (x >= width - bezelZonePx) && (y > cutoutPx);

                trackingTopBezel = inTopZone && !launcherOpen;
                trackingRightBezel = inRightZone && !controlsOpen;

                // Also detect closing swipes when overlays are already open:
                // - In Launcher: swipe UP from anywhere below top header to dismiss
                trackingLauncherClose = launcherOpen && (y > 60.0f * density);
                // - In Browser Controls: swipe RIGHT from right rail zone (within 220dp of right edge) to dismiss
                trackingControlsClose = controlsOpen && (x >= width - 220.0f * density);

                if (trackingTopBezel) {
                    Log.d(TAG, "ACTION_DOWN in Top Bezel zone: (" + x + ", " + y + ")");
                } else if (trackingRightBezel) {
                    Log.d(TAG, "ACTION_DOWN in Right Bezel zone: (" + x + ", " + y + ")");
                }
                return false;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Abort tracking on multi-touch gestures (e.g. pinch-to-zoom)
                trackingTopBezel = false;
                trackingRightBezel = false;
                trackingLauncherClose = false;
                trackingControlsClose = false;
                return false;

            case MotionEvent.ACTION_MOVE:
                if (gestureIntercepted) {
                    return true;
                }

                float dx = x - downX;
                float dy = y - downY;

                velocityTracker.computeCurrentVelocity(1000);
                float vx = velocityTracker.getXVelocity();
                float vy = velocityTracker.getYVelocity();

                // --- 1. TOP BEZEL SWIPE (DOWNWARD -> LAUNCHER) ---
                if (trackingTopBezel) {
                    // Abort ONLY if user swiped significantly off the top edge,
                    // or moved horizontally > 36dp while vertical travel is minimal (< 12dp)
                    if (dy < -touchSlop * 2 || (Math.abs(dx) > 36.0f * density && dy < 12.0f * density)) {
                        Log.d(TAG, "Aborted Top Bezel tracking: dx=" + dx + ", dy=" + dy);
                        trackingTopBezel = false;
                        return false;
                    }

                    boolean distanceMet = (dy >= swipeThresholdPx && dy >= Math.abs(dx));
                    boolean flingMet = (vy >= flingVelocityPx && dy >= 16.0f * density && vy >= Math.abs(vx));

                    if (distanceMet || flingMet) {
                        Log.d(TAG, "Top Bezel Swipe Triggered! dy=" + dy + ", vy=" + vy);
                        gestureIntercepted = true;
                        trackingTopBezel = false;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (listener != null) {
                            listener.onTopBezelSwipe();
                        }
                        return true;
                    }
                }

                // --- 2. RIGHT BEZEL SWIPE (LEFTWARD -> BROWSER CONTROLS) ---
                else if (trackingRightBezel) {
                    // Abort ONLY if user swiped significantly off the right edge,
                    // or moved vertically > 36dp while horizontal travel is minimal (< 12dp)
                    if (dx > touchSlop * 2 || (Math.abs(dy) > 36.0f * density && Math.abs(dx) < 12.0f * density)) {
                        Log.d(TAG, "Aborted Right Bezel tracking: dx=" + dx + ", dy=" + dy);
                        trackingRightBezel = false;
                        return false;
                    }

                    boolean distanceMet = (dx <= -swipeThresholdPx && Math.abs(dx) >= Math.abs(dy));
                    boolean flingMet = (vx <= -flingVelocityPx && dx <= -16.0f * density && Math.abs(vx) >= Math.abs(vy));

                    if (distanceMet || flingMet) {
                        Log.d(TAG, "Right Bezel Swipe Triggered! dx=" + dx + ", vx=" + vx);
                        gestureIntercepted = true;
                        trackingRightBezel = false;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (listener != null) {
                            listener.onRightBezelSwipe();
                        }
                        return true;
                    }
                }

                // --- 3. SWIPE UP TO CLOSE LAUNCHER ---
                else if (trackingLauncherClose) {
                    // Abort if user moves horizontally to scroll cards
                    if (Math.abs(dx) > 24.0f * density) {
                        trackingLauncherClose = false;
                        return false;
                    }

                    boolean distanceMet = (dy <= -swipeThresholdPx * 1.2f && Math.abs(dy) >= Math.abs(dx));
                    boolean flingMet = (vy <= -flingVelocityPx && dy <= -20.0f * density && Math.abs(vy) >= Math.abs(vx));

                    if (distanceMet || flingMet) {
                        Log.d(TAG, "Swipe Up to Close Launcher Triggered! dy=" + dy + ", vy=" + vy);
                        gestureIntercepted = true;
                        trackingLauncherClose = false;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (listener != null) {
                            listener.onSwipeUpToCloseLauncher();
                        }
                        return true;
                    }
                }

                // --- 4. SWIPE RIGHT TO CLOSE BROWSER CONTROLS ---
                else if (trackingControlsClose) {
                    // Abort if user moves vertically on the controls rail
                    if (Math.abs(dy) > 28.0f * density && dx < 12.0f * density) {
                        trackingControlsClose = false;
                        return false;
                    }

                    boolean distanceMet = (dx >= swipeThresholdPx && dx >= Math.abs(dy));
                    boolean flingMet = (vx >= flingVelocityPx && dx >= 16.0f * density && vx >= Math.abs(vy));

                    if (distanceMet || flingMet) {
                        Log.d(TAG, "Swipe Right to Close Controls Triggered! dx=" + dx + ", vx=" + vx);
                        gestureIntercepted = true;
                        trackingControlsClose = false;
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (listener != null) {
                            listener.onSwipeRightToCloseControls();
                        }
                        return true;
                    }
                }

                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackingTopBezel = false;
                trackingRightBezel = false;
                trackingLauncherClose = false;
                trackingControlsClose = false;
                gestureIntercepted = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return false;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (gestureIntercepted) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureIntercepted = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
