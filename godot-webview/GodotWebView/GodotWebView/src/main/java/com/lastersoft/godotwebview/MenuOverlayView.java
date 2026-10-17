package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.PathParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the centered 2x scaled menu card, fullscreen outside-click dismissal scrim,
 * quick site shortcuts, collapsible extra app categories accordion, and bottom control row.
 */
public class MenuOverlayView {

    public static final float MENU_SCALE = 2.0f;

    public interface MenuActionListener {
        void onSiteSelected(String url);
        void onGoBack();
        void onZoomIn();
        void onZoomOut();
        void onRefresh();
        void onToggleDesktopMode(boolean isLongPressSaveDefault);
        void onToggleRightMargin(boolean isLongPressSaveDefault);
        void onRotateScreen(boolean isLongPressResetLandscape);
        void onCloseMenu();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View scrimView;
    private LinearLayout menuCard;
    private ImageButton backButton;
    private Button desktopModeButton;
    private GradientDrawable desktopModeCircle;
    private ImageButton rightMarginButton;
    private GradientDrawable rightMarginCircle;
    private ControlIconDrawable rightMarginDrawable;
    private Button extraAppsToggleBtn;
    private ScrollView extraAppsScrollView;
    private final List<Runnable> categoryCollapseCallbacks = new ArrayList<>();
    private boolean isMenuOpen = false;

    @SuppressLint("ClickableViewAccessibility")
    public void setup(Activity activity, FrameLayout rootLayout, UserAgentManager uaManager, MenuActionListener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        float menuDensity = density * MENU_SCALE;
        int controlBtnSize = (int) (37 * menuDensity);
        int spacing = (int) (10 * menuDensity);
        int cardPadding = (int) (14 * menuDensity);
        int paddingH = (int) (20 * menuDensity);

        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

        // --- 1. FULLSCREEN BACKDROP SCRIM (OUTSIDE TAP DISMISS ONLY) ---
        scrimView = new View(activity);
        scrimView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrimView.setBackgroundColor(Color.parseColor("#4D000000")); // 30% black
        scrimView.setVisibility(View.GONE);
        scrimView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        isDrag = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (!isDrag && Math.hypot(dx, dy) > touchSlop) {
                            isDrag = true;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            if (listener != null) listener.onCloseMenu();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        isDrag = false;
                        return true;
                }
                return false;
            }
        });

        // --- 2. UNIFIED MENU CARD ---
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(Color.parseColor("#F01A1A1A"));
        cardBg.setCornerRadius(18 * menuDensity);
        cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#33FFFFFF"));

        menuCard = new LinearLayout(activity);
        menuCard.setOrientation(LinearLayout.VERTICAL);
        menuCard.setBackground(cardBg);
        menuCard.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
        // Intercept and consume all touch events landing on the menu card so touches never pass through to the backdrop scrim.
        menuCard.setOnTouchListener((v, event) -> true);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                (int) (365 * menuDensity),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        menuCard.setLayoutParams(cardParams);
        menuCard.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menuCard.setElevation(12 * menuDensity);
        }

        // Quick Sites Buttons
        LinearLayout.LayoutParams siteBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * menuDensity));
        siteBtnParams.setMargins(0, 0, 0, spacing);

        for (AppCatalog.SiteEntry site : AppCatalog.MAIN_SITES) {
            GradientDrawable siteBg = new GradientDrawable();
            siteBg.setShape(GradientDrawable.RECTANGLE);
            siteBg.setColor(Color.parseColor("#2E2E2E"));
            siteBg.setCornerRadius(22 * menuDensity);

            Button siteBtn = new Button(activity);
            siteBtn.setText(site.name);
            siteBtn.setAllCaps(false);
            siteBtn.setTextColor(Color.WHITE);
            siteBtn.setTextSize(15 * MENU_SCALE);
            siteBtn.setBackground(siteBg);
            siteBtn.setPadding(paddingH, 0, paddingH, 0);
            siteBtn.setLayoutParams(siteBtnParams);
            siteBtn.setOnClickListener(v -> {
                if (listener != null) listener.onSiteSelected(site.url);
            });
            menuCard.addView(siteBtn);
        }

        // Consolidated Extra Apps Menu (Collapsible Accordion)
        categoryCollapseCallbacks.clear();
        int totalExtraApps = AppCatalog.getTotalExtraAppsCount();
        if (!AppCatalog.EXTRA_CATEGORIES.isEmpty()) {
            GradientDrawable extraToggleBg = new GradientDrawable();
            extraToggleBg.setShape(GradientDrawable.RECTANGLE);
            extraToggleBg.setColor(Color.parseColor("#1E2433"));
            extraToggleBg.setCornerRadius(22 * menuDensity);
            extraToggleBg.setStroke((int) (1 * menuDensity), Color.parseColor("#5C7CFA"));

            extraAppsToggleBtn = new Button(activity);
            extraAppsToggleBtn.setText("🗂️  Extra Apps (" + totalExtraApps + ")  ▾");
            extraAppsToggleBtn.setAllCaps(false);
            extraAppsToggleBtn.setTextColor(Color.parseColor("#91A7FF"));
            extraAppsToggleBtn.setTextSize(14 * MENU_SCALE);
            extraAppsToggleBtn.setBackground(extraToggleBg);
            extraAppsToggleBtn.setPadding(paddingH, 0, paddingH, 0);
            extraAppsToggleBtn.setLayoutParams(siteBtnParams);

            extraAppsScrollView = new ScrollView(activity) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int maxHeight = (int) (240 * menuDensity);
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
                }
            };
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scrollParams.setMargins(0, 0, 0, spacing);
            extraAppsScrollView.setLayoutParams(scrollParams);
            extraAppsScrollView.setVisibility(View.GONE);

            LinearLayout categoriesContainer = new LinearLayout(activity);
            categoriesContainer.setOrientation(LinearLayout.VERTICAL);
            categoriesContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (AppCatalog.CategoryEntry category : AppCatalog.EXTRA_CATEGORIES) {
                if (category.sites.isEmpty()) continue;

                GradientDrawable catHeaderBg = new GradientDrawable();
                catHeaderBg.setShape(GradientDrawable.RECTANGLE);
                catHeaderBg.setColor(Color.parseColor("#23293A"));
                catHeaderBg.setCornerRadius(18 * menuDensity);
                catHeaderBg.setStroke((int) (1 * menuDensity), Color.parseColor("#3B4D7A"));

                final String collapsedTitle = category.title + " (" + category.sites.size() + ")  ▾";
                final String expandedTitle = category.title + " (" + category.sites.size() + ")  ▴";

                final Button catToggleBtn = new Button(activity);
                catToggleBtn.setText(collapsedTitle);
                catToggleBtn.setAllCaps(false);
                catToggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
                catToggleBtn.setTextColor(Color.parseColor("#B4C6FF"));
                catToggleBtn.setTextSize(13.5f * MENU_SCALE);
                catToggleBtn.setBackground(catHeaderBg);
                catToggleBtn.setPadding(paddingH, 0, paddingH, 0);

                LinearLayout.LayoutParams catBtnParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * menuDensity));
                catBtnParams.setMargins(0, 0, 0, (int) (6 * menuDensity));
                catToggleBtn.setLayoutParams(catBtnParams);

                final LinearLayout catItemsContainer = new LinearLayout(activity);
                catItemsContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams catItemsParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                catItemsParams.setMargins((int) (8 * menuDensity), 0, (int) (8 * menuDensity), (int) (4 * menuDensity));
                catItemsContainer.setLayoutParams(catItemsParams);
                catItemsContainer.setVisibility(View.GONE);

                for (AppCatalog.SiteEntry site : category.sites) {
                    GradientDrawable itemBg = new GradientDrawable();
                    itemBg.setShape(GradientDrawable.RECTANGLE);
                    itemBg.setColor(Color.parseColor("#2A2D35"));
                    itemBg.setCornerRadius(16 * menuDensity);

                    Button itemBtn = new Button(activity);
                    itemBtn.setText(site.name);
                    itemBtn.setAllCaps(false);
                    itemBtn.setTextColor(Color.parseColor("#E8E8E8"));
                    itemBtn.setTextSize(13 * MENU_SCALE);
                    itemBtn.setBackground(itemBg);
                    itemBtn.setPadding(paddingH, 0, paddingH, 0);

                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * menuDensity));
                    itemParams.setMargins(0, 0, 0, (int) (6 * menuDensity));
                    itemBtn.setLayoutParams(itemParams);

                    itemBtn.setOnClickListener(v -> {
                        if (listener != null) listener.onSiteSelected(site.url);
                    });
                    catItemsContainer.addView(itemBtn);
                }

                final Runnable collapseThisCategory = () -> {
                    catItemsContainer.setVisibility(View.GONE);
                    catToggleBtn.setText(collapsedTitle);
                };
                categoryCollapseCallbacks.add(collapseThisCategory);

                catToggleBtn.setOnClickListener(v -> {
                    if (catItemsContainer.getVisibility() == View.VISIBLE) {
                        collapseThisCategory.run();
                    } else {
                        for (Runnable collapse : categoryCollapseCallbacks) {
                            collapse.run();
                        }
                        catItemsContainer.setVisibility(View.VISIBLE);
                        catToggleBtn.setText(expandedTitle);
                        extraAppsScrollView.post(() -> extraAppsScrollView.smoothScrollTo(0, catToggleBtn.getTop()));
                    }
                    menuCard.post(() -> repositionMenuCard(rootLayout));
                });

                categoriesContainer.addView(catToggleBtn);
                categoriesContainer.addView(catItemsContainer);
            }

            extraAppsScrollView.addView(categoriesContainer);

            extraAppsToggleBtn.setOnClickListener(v -> {
                if (extraAppsScrollView.getVisibility() == View.VISIBLE) {
                    for (Runnable collapse : categoryCollapseCallbacks) {
                        collapse.run();
                    }
                    extraAppsScrollView.setVisibility(View.GONE);
                    extraAppsToggleBtn.setText("🗂️  Extra Apps (" + totalExtraApps + ")  ▾");
                } else {
                    extraAppsScrollView.setVisibility(View.VISIBLE);
                    extraAppsToggleBtn.setText("🗂️  Extra Apps (" + totalExtraApps + ")  ▴");
                }
                menuCard.post(() -> repositionMenuCard(rootLayout));
            });

            menuCard.addView(extraAppsToggleBtn);
            menuCard.addView(extraAppsScrollView);
        }

        // Controls Row
        LinearLayout controlsRow = new LinearLayout(activity);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.gravity = Gravity.CENTER_HORIZONTAL;
        controlsRow.setLayoutParams(rowParams);

        LinearLayout.LayoutParams ctrlParams = new LinearLayout.LayoutParams(controlBtnSize, controlBtnSize);
        ctrlParams.setMargins(0, 0, (int) (5 * menuDensity), 0);

        LinearLayout.LayoutParams lastCtrlParams = new LinearLayout.LayoutParams(controlBtnSize, controlBtnSize);
        lastCtrlParams.setMargins(0, 0, 0, 0);

        GradientDrawable controlCircle = new GradientDrawable();
        controlCircle.setShape(GradientDrawable.OVAL);
        controlCircle.setColor(Color.parseColor("#2E2E2E"));

        GradientDrawable backCircle = new GradientDrawable();
        backCircle.setShape(GradientDrawable.OVAL);
        backCircle.setColor(Color.parseColor("#2E2E2E"));

        backButton = new ImageButton(activity);
        backButton.setBackground(backCircle);
        backButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.BACK));
        backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setLayoutParams(ctrlParams);
        backButton.setContentDescription("Go Back");
        backButton.setOnClickListener(v -> {
            if (listener != null) listener.onGoBack();
        });

        GradientDrawable zoomInCircle = new GradientDrawable();
        zoomInCircle.setShape(GradientDrawable.OVAL);
        zoomInCircle.setColor(Color.parseColor("#2E2E2E"));

        ImageButton zoomInButton = new ImageButton(activity);
        zoomInButton.setBackground(zoomInCircle);
        zoomInButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.ZOOM_IN));
        zoomInButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        zoomInButton.setPadding(0, 0, 0, 0);
        zoomInButton.setLayoutParams(ctrlParams);
        zoomInButton.setContentDescription("Zoom In");
        zoomInButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomIn();
        });

        ImageButton zoomOutButton = new ImageButton(activity);
        zoomOutButton.setBackground(controlCircle);
        zoomOutButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.ZOOM_OUT));
        zoomOutButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        zoomOutButton.setPadding(0, 0, 0, 0);
        zoomOutButton.setLayoutParams(ctrlParams);
        zoomOutButton.setContentDescription("Zoom Out");
        zoomOutButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomOut();
        });

        GradientDrawable refreshCircle = new GradientDrawable();
        refreshCircle.setShape(GradientDrawable.OVAL);
        refreshCircle.setColor(Color.parseColor("#2E2E2E"));

        ImageButton refreshButton = new ImageButton(activity);
        refreshButton.setBackground(refreshCircle);
        refreshButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.REFRESH));
        refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        refreshButton.setPadding(0, 0, 0, 0);
        refreshButton.setLayoutParams(ctrlParams);
        refreshButton.setContentDescription("Refresh Page");
        refreshButton.setOnClickListener(v -> {
            if (listener != null) listener.onRefresh();
        });

        desktopModeCircle = new GradientDrawable();
        desktopModeCircle.setShape(GradientDrawable.OVAL);

        desktopModeButton = new Button(activity);
        desktopModeButton.setTextSize(15 * MENU_SCALE);
        desktopModeButton.setPadding(0, 0, 0, 0);
        desktopModeButton.setIncludeFontPadding(false);
        desktopModeButton.setGravity(Gravity.CENTER);
        desktopModeButton.setBackground(desktopModeCircle);
        desktopModeButton.setLayoutParams(ctrlParams);
        desktopModeButton.setContentDescription("Toggle Desktop/Mobile User-Agent");
        if (uaManager != null) {
            uaManager.updateDesktopButtonStyle(desktopModeButton, desktopModeCircle, menuDensity);
        }

        desktopModeButton.setOnTouchListener(new View.OnTouchListener() {
            private boolean longPressTriggered = false;
            private final Runnable longPressRunnable = () -> {
                longPressTriggered = true;
                desktopModeButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                desktopModeButton.setAlpha(1.0f);
                if (listener != null) listener.onToggleDesktopMode(true);
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longPressTriggered = false;
                        v.setAlpha(0.7f);
                        handler.postDelayed(longPressRunnable, 800);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float mx = event.getX();
                        float my = event.getY();
                        if (mx < -touchSlop || mx > v.getWidth() + touchSlop || my < -touchSlop || my > v.getHeight() + touchSlop) {
                            handler.removeCallbacks(longPressRunnable);
                            v.setAlpha(1.0f);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.setAlpha(1.0f);
                        handler.removeCallbacks(longPressRunnable);
                        if (!longPressTriggered) {
                            if (listener != null) listener.onToggleDesktopMode(false);
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(1.0f);
                        handler.removeCallbacks(longPressRunnable);
                        return true;
                }
                return false;
            }
        });

        rightMarginCircle = new GradientDrawable();
        rightMarginCircle.setShape(GradientDrawable.OVAL);
        rightMarginCircle.setColor(Color.parseColor("#2E2E2E"));

        rightMarginDrawable = new ControlIconDrawable(ControlIconDrawable.Type.RIGHT_MARGIN);

        rightMarginButton = new ImageButton(activity);
        rightMarginButton.setBackground(rightMarginCircle);
        rightMarginButton.setImageDrawable(rightMarginDrawable);
        rightMarginButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rightMarginButton.setPadding(0, 0, 0, 0);
        rightMarginButton.setLayoutParams(ctrlParams);
        rightMarginButton.setContentDescription("Toggle Right Margin (Clear Board Menu)");

        rightMarginButton.setOnTouchListener(new View.OnTouchListener() {
            private boolean longPressTriggered = false;
            private final Runnable longPressRunnable = () -> {
                longPressTriggered = true;
                rightMarginButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                rightMarginButton.setAlpha(1.0f);
                if (listener != null) listener.onToggleRightMargin(true);
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longPressTriggered = false;
                        v.setAlpha(0.7f);
                        handler.postDelayed(longPressRunnable, 800);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float mx = event.getX();
                        float my = event.getY();
                        if (mx < -touchSlop || mx > v.getWidth() + touchSlop || my < -touchSlop || my > v.getHeight() + touchSlop) {
                            handler.removeCallbacks(longPressRunnable);
                            v.setAlpha(1.0f);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.setAlpha(1.0f);
                        handler.removeCallbacks(longPressRunnable);
                        if (!longPressTriggered) {
                            if (listener != null) listener.onToggleRightMargin(false);
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(1.0f);
                        handler.removeCallbacks(longPressRunnable);
                        return true;
                }
                return false;
            }
        });

        GradientDrawable rotateCircle = new GradientDrawable();
        rotateCircle.setShape(GradientDrawable.OVAL);
        rotateCircle.setColor(Color.parseColor("#2E2E2E"));

        Button rotateButton = new Button(activity);
        rotateButton.setText("90°");
        rotateButton.setTypeface(Typeface.DEFAULT_BOLD);
        rotateButton.setTextColor(Color.WHITE);
        rotateButton.setTextSize(13 * MENU_SCALE);
        rotateButton.setPadding(0, 0, 0, 0);
        rotateButton.setIncludeFontPadding(false);
        rotateButton.setGravity(Gravity.CENTER);
        rotateButton.setBackground(rotateCircle);
        rotateButton.setLayoutParams(ctrlParams);
        rotateButton.setContentDescription("Rotate Screen");
        rotateButton.setOnClickListener(v -> {
            if (listener != null) listener.onRotateScreen(false);
        });
        rotateButton.setOnLongClickListener(v -> {
            if (listener != null) listener.onRotateScreen(true);
            return true;
        });

        GradientDrawable closeCircle = new GradientDrawable();
        closeCircle.setShape(GradientDrawable.OVAL);
        closeCircle.setColor(Color.parseColor("#2E2E2E"));

        ImageButton closeMenuButton = new ImageButton(activity);
        closeMenuButton.setBackground(closeCircle);
        closeMenuButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.CLOSE));
        closeMenuButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeMenuButton.setPadding(0, 0, 0, 0);
        closeMenuButton.setLayoutParams(lastCtrlParams);
        closeMenuButton.setContentDescription("Close Menu");
        closeMenuButton.setOnClickListener(v -> {
            if (listener != null) listener.onCloseMenu();
        });

        controlsRow.addView(backButton);
        controlsRow.addView(zoomInButton);
        controlsRow.addView(zoomOutButton);
        controlsRow.addView(refreshButton);
        controlsRow.addView(desktopModeButton);
        controlsRow.addView(rightMarginButton);
        controlsRow.addView(rotateButton);
        controlsRow.addView(closeMenuButton);

        menuCard.addView(controlsRow);

        rootLayout.addView(scrimView);
        rootLayout.addView(menuCard);
    }

    public View getScrimView() {
        return scrimView;
    }

    public LinearLayout getMenuCard() {
        return menuCard;
    }

    public boolean isMenuOpen() {
        return isMenuOpen;
    }

    public void showMenu(FrameLayout rootLayout, boolean canGoBack) {
        if (menuCard == null || rootLayout == null) return;
        isMenuOpen = true;
        updateBackButtonState(canGoBack);

        scrimView.setVisibility(View.VISIBLE);
        menuCard.setVisibility(View.VISIBLE);

        repositionMenuCard(rootLayout);
    }

    public void hideMenu() {
        if (!isMenuOpen) return;
        isMenuOpen = false;
        if (menuCard != null) menuCard.setVisibility(View.GONE);
        if (scrimView != null) scrimView.setVisibility(View.GONE);
        for (Runnable collapse : categoryCollapseCallbacks) {
            collapse.run();
        }
        if (extraAppsScrollView != null) extraAppsScrollView.setVisibility(View.GONE);
        if (extraAppsToggleBtn != null) {
            extraAppsToggleBtn.setText("🗂️  Extra Apps (" + AppCatalog.getTotalExtraAppsCount() + ")  ▾");
        }
    }

    public void repositionMenuCard(ViewGroup rootLayout) {
        if (menuCard == null || rootLayout == null) return;
        float density = rootLayout.getResources().getDisplayMetrics().density;
        int margin = (int) (16 * density);

        menuCard.measure(
                View.MeasureSpec.makeMeasureSpec(rootLayout.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(rootLayout.getHeight(), View.MeasureSpec.AT_MOST)
        );
        int menuWidth = menuCard.getMeasuredWidth();
        int menuHeight = menuCard.getMeasuredHeight();

        float targetX = (rootLayout.getWidth() - menuWidth) / 2f;
        float targetY = (rootLayout.getHeight() - menuHeight) / 2f;

        targetX = Math.max(margin, Math.min(targetX, rootLayout.getWidth() - menuWidth - margin));
        targetY = Math.max(margin, Math.min(targetY, rootLayout.getHeight() - menuHeight - margin));

        menuCard.setX(targetX);
        menuCard.setY(targetY);
    }

    public void updateBackButtonState(boolean canBack) {
        if (backButton == null) return;
        backButton.setAlpha(canBack ? 1.0f : 0.35f);
        backButton.setEnabled(canBack);
    }

    public void updateDesktopButtonStyle(UserAgentManager uaManager, float menuDensity) {
        if (uaManager != null && desktopModeButton != null && desktopModeCircle != null) {
            uaManager.updateDesktopButtonStyle(desktopModeButton, desktopModeCircle, menuDensity);
        }
    }

    public void updateRightMarginButtonStyle(boolean isActive, float menuDensity) {
        if (rightMarginButton == null || rightMarginCircle == null) return;
        if (isActive) {
            rightMarginCircle.setColor(Color.parseColor("#1E2433"));
            rightMarginCircle.setStroke((int) (1.5f * menuDensity), Color.parseColor("#5C7CFA"));
            if (rightMarginDrawable != null) {
                rightMarginDrawable.setIconColor(Color.parseColor("#91A7FF"));
            }
        } else {
            rightMarginCircle.setColor(Color.parseColor("#2E2E2E"));
            rightMarginCircle.setStroke(0, Color.TRANSPARENT);
            if (rightMarginDrawable != null) {
                rightMarginDrawable.setIconColor(Color.WHITE);
            }
        }
    }

    public void bringToFront() {
        if (scrimView != null) scrimView.bringToFront();
        if (menuCard != null) menuCard.bringToFront();
    }

    public void destroy(FrameLayout rootLayout) {
        hideMenu();
        categoryCollapseCallbacks.clear();
        if (scrimView != null && rootLayout != null) {
            rootLayout.removeView(scrimView);
            scrimView = null;
        }
        if (menuCard != null && rootLayout != null) {
            rootLayout.removeView(menuCard);
            menuCard = null;
        }
        backButton = null;
        desktopModeButton = null;
        desktopModeCircle = null;
        rightMarginButton = null;
        rightMarginCircle = null;
        rightMarginDrawable = null;
        extraAppsToggleBtn = null;
        extraAppsScrollView = null;
    }

    /**
     * Renders mathematically and optically centered Material Design vector icons
     * (Back, Zoom In, Zoom Out, Refresh, Close Menu) with unified stroke weight,
     * crisp scaling, and identical visual geometry.
     */
    public static class ControlIconDrawable extends Drawable {
        public enum Type {
            BACK("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"),
            ZOOM_OUT("M19 13H5v-2h14v2z"),
            ZOOM_IN("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
            REFRESH("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"),
            RIGHT_MARGIN("M16 20H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2h12c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2zm0-14H4v12h12V6zM20 4h2v16h-2z"),
            HELP("M11.07 12.85c.77-1.39 2.25-2.21 3.11-3.44.91-1.29.4-4.05-2.18-4.05-1.9 0-2.67 1.48-2.67 2.68H7.1C7.1 4.7 9.33 3 12 3c2.65 0 4.22 1.72 4.22 4.07 0 1.94-1.22 3.03-2.14 3.9-.92.88-1.57 1.49-1.89 2.45-.19.57-.22 1.1-.22 2.48h-2.1c0-1.82-.03-2.61.28-3.05zM11 18h2v2h-2z"),
            CLOSE("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z");

            final String pathData;

            Type(String pathData) {
                this.pathData = pathData;
            }
        }

        private final Path path;
        private final Paint paint;

        public ControlIconDrawable(Type type) {
            Path p = null;
            try {
                p = PathParser.createPathFromPathData(type.pathData);
            } catch (Exception ignored) {
            }
            this.path = p;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setStyle(Paint.Style.FILL);
            this.paint.setColor(Color.WHITE);
        }

        public void setIconColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty() || path == null) return;
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float size = Math.min(b.width(), b.height());
            float iconBox = size * 0.51f;
            float scale = iconBox / 24.0f;

            canvas.save();
            canvas.translate(cx - 12.0f * scale, cy - 12.0f * scale);
            canvas.scale(scale, scale);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return -1;
        }

        @Override
        public int getIntrinsicHeight() {
            return -1;
        }
    }
}
