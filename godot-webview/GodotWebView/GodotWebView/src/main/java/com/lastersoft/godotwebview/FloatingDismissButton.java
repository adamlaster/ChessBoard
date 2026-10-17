package com.lastersoft.godotwebview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

/**
 * Draggable floating action button with boundary clamping and 4-second idle auto-fade (35% alpha).
 * Primarily used as the floating dismiss button ("✕") to close secondary tabs and return to the active game.
 */
public class FloatingDismissButton {

    public interface OnButtonClickListener {
        void onClick();
    }

    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private Runnable idleFadeRunnable;
    private Button button;
    private ViewGroup container;
    private int buttonSize;
    private int margin;
    private float density;

    public Button createButton(Activity activity, ViewGroup containerLayout, OnButtonClickListener clickListener) {
        this.container = containerLayout;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.buttonSize = (int) (58 * density);
        this.margin = (int) (16 * density);

        button = new Button(activity);
        button.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(8 * density);
        }

        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        button.setLayoutParams(fabParams);

        idleFadeRunnable = () -> {
            if (button != null && button.getVisibility() == View.VISIBLE) {
                button.animate().alpha(0.35f).setDuration(500).start();
            }
        };

        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

        button.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float downRawX, downRawY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dX = v.getX() - downRawX;
                        dY = v.getY() - downRawY;
                        isDragging = false;
                        resetIdleFade(true);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (!isDragging && Math.hypot(deltaX, deltaY) > touchSlop) {
                            isDragging = true;
                        }
                        if (isDragging && container != null) {
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;
                            float maxX = container.getWidth() - v.getWidth() - margin;
                            float maxY = container.getHeight() - v.getHeight() - margin;
                            newX = Math.max(margin, Math.min(newX, maxX));
                            newY = Math.max(margin, Math.min(newY, maxY));
                            v.setX(newX);
                            v.setY(newY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            if (clickListener != null) {
                                clickListener.onClick();
                            }
                        } else {
                            scheduleIdleFade();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        scheduleIdleFade();
                        return true;
                }
                return false;
            }
        });

        return button;
    }

    public Button getButton() {
        return button;
    }

    public void showDismissStyle() {
        if (button == null || container == null) return;

        button.setText("✕");
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);

        GradientDrawable dismissCircle = new GradientDrawable();
        dismissCircle.setShape(GradientDrawable.OVAL);
        dismissCircle.setColor(Color.parseColor("#E61E1E1E"));
        dismissCircle.setStroke((int) (2.5f * density), Color.parseColor("#E53935"));
        button.setBackground(dismissCircle);
        button.setContentDescription("Close Tab and Return to Game");

        // Snap to bottom-right corner if not already moved
        if (button.getX() <= margin && button.getY() <= margin) {
            pinToBottomRight(container.getWidth(), container.getHeight());
        }

        button.setAlpha(1.0f);
        button.setVisibility(View.VISIBLE);
        button.bringToFront();
        scheduleIdleFade();
    }

    public void pinToBottomRight(int containerWidth, int containerHeight) {
        if (button == null) return;
        float targetX = containerWidth - buttonSize - margin;
        float targetY = containerHeight - buttonSize - margin;
        if (targetX > 0 && targetY > 0) {
            button.setX(targetX);
            button.setY(targetY);
        }
    }

    public void hide() {
        if (button != null) {
            button.animate().cancel();
            button.setVisibility(View.GONE);
        }
        resetIdleFade(false);
    }

    public void bringToFront() {
        if (button != null && button.getVisibility() == View.VISIBLE) {
            button.bringToFront();
        }
    }

    public void scheduleIdleFade() {
        if (idleHandler != null && idleFadeRunnable != null) {
            idleHandler.removeCallbacks(idleFadeRunnable);
            idleHandler.postDelayed(idleFadeRunnable, 4000);
        }
    }

    public void resetIdleFade(boolean makeOpaque) {
        if (idleHandler != null && idleFadeRunnable != null) {
            idleHandler.removeCallbacks(idleFadeRunnable);
        }
        if (makeOpaque && button != null) {
            button.animate().alpha(1.0f).setDuration(150).start();
        }
    }

    public boolean isVisible() {
        return button != null && button.getVisibility() == View.VISIBLE;
    }

    public int getButtonSize() {
        return buttonSize;
    }

    public int getMargin() {
        return margin;
    }

    public void destroy() {
        resetIdleFade(false);
        if (button != null) {
            button.animate().cancel();
            if (container != null) {
                container.removeView(button);
            }
            button = null;
        }
        container = null;
    }
}

