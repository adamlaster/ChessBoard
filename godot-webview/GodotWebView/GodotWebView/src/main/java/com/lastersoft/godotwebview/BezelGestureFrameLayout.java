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
 *
 * Implements generous edge-zone thresholding (64dp), velocity/fling detection,
 * multi-touch pinch safety, and Board OS top-right cutout protection to ensure
 * zero interference with normal chessboard play, piece dragging, or Board hardware buttons.
 *
 * When an overlay (Launcher or Controls) is already open, bezel swipe tracking is completely
 * bypassed so all touches reach cards, buttons, and scroll views with zero interference.
 */
public class BezelGestureFrameLayout extends FrameLayout {

    private static final String TAG = "GodotWebView.Bezel";

    public interface BezelSwipeListener {
        void onTopBezelSwipe();
        void onRightBezelSwipe();
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
        // If an edge gesture is actively being tracked, do not allow child views
        // (e.g. WebView scroll) to disallow interception.
        if (trackingTopBezel || trackingRightBezel) {
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

                // When Launcher is open, completely bypass all bezel interception
                // so that app cards, scrolls, and deadspace dismissal get 100% clean touches.
                if (launcherOpen) {
                    trackingTopBezel = false;
                    trackingRightBezel = false;
                    return false;
                }

                // 1. Check Top Bezel: touch starts near top edge, excluding the Board OS cutout at top-right
                boolean inTopZone = (y <= bezelZonePx) && (x < width - cutoutPx);
                // 2. Check Right Bezel: touch starts near right edge, excluding the Board OS cutout at top-right
                boolean inRightZone = (x >= width - bezelZonePx) && (y > cutoutPx);

                trackingTopBezel = inTopZone;
                trackingRightBezel = inRightZone && !controlsOpen;

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
                    if (checkTopBezelSwipe(dx, dy, vx, vy, swipeThresholdPx, flingVelocityPx)) {
                        return true;
                    }
                }
                // --- 2. RIGHT BEZEL SWIPE (LEFTWARD -> BROWSER CONTROLS) ---
                else if (trackingRightBezel) {
                    if (checkRightBezelSwipe(dx, dy, vx, vy, swipeThresholdPx, flingVelocityPx)) {
                        return true;
                    }
                }

                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackingTopBezel = false;
                trackingRightBezel = false;
                gestureIntercepted = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return false;
        }

        return super.onInterceptTouchEvent(ev);
    }

    private boolean checkTopBezelSwipe(float dx, float dy, float vx, float vy, float swipeThresholdPx, float flingVelocityPx) {
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
        return false;
    }

    private boolean checkRightBezelSwipe(float dx, float dy, float vx, float vy, float swipeThresholdPx, float flingVelocityPx) {
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
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getX();
        float y = ev.getY();

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        if (gestureIntercepted) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureIntercepted = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            return true;
        }

        // When tracking a bezel gesture and no child view consumed ACTION_DOWN
        // (e.g. touch landed in the 106dp right gutter/margin during Duo mode),
        // onTouchEvent must consume the touch stream and detect the swipe directly.
        if (trackingTopBezel || trackingRightBezel) {
            float swipeThresholdPx = SWIPE_THRESHOLD_DP * density;
            float flingVelocityPx = FLING_VELOCITY_DP * density;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    // Touch landed in empty margin/gutter area. Claim the gesture!
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - downX;
                    float dy = y - downY;
                    velocityTracker.computeCurrentVelocity(1000);
                    float vx = velocityTracker.getXVelocity();
                    float vy = velocityTracker.getYVelocity();

                    if (trackingTopBezel) {
                        checkTopBezelSwipe(dx, dy, vx, vy, swipeThresholdPx, flingVelocityPx);
                    } else if (trackingRightBezel) {
                        checkRightBezelSwipe(dx, dy, vx, vy, swipeThresholdPx, flingVelocityPx);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    trackingTopBezel = false;
                    trackingRightBezel = false;
                    gestureIntercepted = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    return true;
            }
        }

        return super.onTouchEvent(ev);
    }
}
