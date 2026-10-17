package com.lastersoft.godotwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
        void onRotateScreen(boolean isLongPressResetLandscape);
        void onCloseMenu();
        void onExitRequested();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View scrimView;
    private LinearLayout menuCard;
    private Button backButton;
    private Button desktopModeButton;
    private GradientDrawable desktopModeCircle;
    private Button extraAppsToggleBtn;
    private ScrollView extraAppsScrollView;
    private final List<Runnable> categoryCollapseCallbacks = new ArrayList<>();
    private TextView slideThumb;
    private TextView slideLabel;
    private boolean isMenuOpen = false;

    @SuppressLint("ClickableViewAccessibility")
    public void setup(Activity activity, FrameLayout rootLayout, UserAgentManager uaManager, MenuActionListener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        float menuDensity = density * MENU_SCALE;
        int controlBtnSize = (int) (37 * menuDensity);
        int spacing = (int) (10 * menuDensity);
        int cardPadding = (int) (14 * menuDensity);
        int paddingH = (int) (20 * menuDensity);

        // --- 1. FULLSCREEN BACKDROP SCRIM (OUTSIDE CLICK DISMISS) ---
        scrimView = new View(activity);
        scrimView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrimView.setBackgroundColor(Color.parseColor("#4D000000")); // 30% black
        scrimView.setVisibility(View.GONE);
        scrimView.setOnClickListener(v -> {
            if (listener != null) listener.onCloseMenu();
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
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                (int) (320 * menuDensity),
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

        backButton = new Button(activity);
        backButton.setText("⬅");
        backButton.setTextColor(Color.WHITE);
        backButton.setTextSize(16 * MENU_SCALE);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setBackground(backCircle);
        backButton.setLayoutParams(ctrlParams);
        backButton.setContentDescription("Go Back");
        backButton.setOnClickListener(v -> {
            if (listener != null) listener.onGoBack();
        });

        Button zoomOutButton = new Button(activity);
        zoomOutButton.setText("－");
        zoomOutButton.setTextColor(Color.WHITE);
        zoomOutButton.setTextSize(16 * MENU_SCALE);
        zoomOutButton.setPadding(0, 0, 0, 0);
        zoomOutButton.setBackground(controlCircle);
        zoomOutButton.setLayoutParams(ctrlParams);
        zoomOutButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomOut();
        });

        GradientDrawable zoomInCircle = new GradientDrawable();
        zoomInCircle.setShape(GradientDrawable.OVAL);
        zoomInCircle.setColor(Color.parseColor("#2E2E2E"));

        Button zoomInButton = new Button(activity);
        zoomInButton.setText("＋");
        zoomInButton.setTextColor(Color.WHITE);
        zoomInButton.setTextSize(16 * MENU_SCALE);
        zoomInButton.setPadding(0, 0, 0, 0);
        zoomInButton.setBackground(zoomInCircle);
        zoomInButton.setLayoutParams(ctrlParams);
        zoomInButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomIn();
        });

        GradientDrawable refreshCircle = new GradientDrawable();
        refreshCircle.setShape(GradientDrawable.OVAL);
        refreshCircle.setColor(Color.parseColor("#2E2E2E"));

        Button refreshButton = new Button(activity);
        refreshButton.setText("↺");
        refreshButton.setTextColor(Color.WHITE);
        refreshButton.setTextSize(18 * MENU_SCALE);
        refreshButton.setPadding(0, 0, 0, 0);
        refreshButton.setBackground(refreshCircle);
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
                desktopModeButton.setAlpha(1.0f);
                if (listener != null) listener.onToggleDesktopMode(true);
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longPressTriggered = false;
                        v.setAlpha(0.7f);
                        handler.postDelayed(longPressRunnable, 3000);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float mx = event.getX();
                        float my = event.getY();
                        if (mx < 0 || mx > v.getWidth() || my < 0 || my > v.getHeight()) {
                            handler.removeCallbacks(longPressRunnable);
                            v.setAlpha(1.0f);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPressRunnable);
                        v.setAlpha(1.0f);
                        if (!longPressTriggered) {
                            float ux = event.getX();
                            float uy = event.getY();
                            if (ux >= 0 && ux <= v.getWidth() && uy >= 0 && uy <= v.getHeight()) {
                                if (listener != null) listener.onToggleDesktopMode(false);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        v.setAlpha(1.0f);
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

        Button closeMenuButton = new Button(activity);
        closeMenuButton.setText("⬇");
        closeMenuButton.setTextColor(Color.WHITE);
        closeMenuButton.setTextSize(16 * MENU_SCALE);
        closeMenuButton.setPadding(0, 0, 0, 0);
        closeMenuButton.setBackground(closeCircle);
        closeMenuButton.setLayoutParams(lastCtrlParams);
        closeMenuButton.setContentDescription("Close Menu");
        closeMenuButton.setOnClickListener(v -> {
            if (listener != null) listener.onCloseMenu();
        });

        controlsRow.addView(backButton);
        controlsRow.addView(zoomOutButton);
        controlsRow.addView(zoomInButton);
        controlsRow.addView(refreshButton);
        controlsRow.addView(desktopModeButton);
        controlsRow.addView(rotateButton);
        controlsRow.addView(closeMenuButton);

        // Slide to Exit Bar
        FrameLayout slideTrack = new FrameLayout(activity);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * menuDensity));
        trackParams.setMargins(0, (int) (14 * menuDensity), 0, 0);
        slideTrack.setLayoutParams(trackParams);

        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setShape(GradientDrawable.RECTANGLE);
        trackBg.setColor(Color.parseColor("#252525"));
        trackBg.setCornerRadius(23 * menuDensity);
        trackBg.setStroke((int) (1 * menuDensity), Color.parseColor("#33FFFFFF"));
        slideTrack.setBackground(trackBg);

        slideLabel = new TextView(activity);
        slideLabel.setText("Slide to Exit ❯❯❯");
        slideLabel.setTextColor(Color.parseColor("#99FFFFFF"));
        slideLabel.setTextSize(14 * MENU_SCALE);
        slideLabel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        slideLabel.setLayoutParams(labelParams);
        slideTrack.addView(slideLabel);

        int thumbSize = (int) (38 * menuDensity);
        int thumbMargin = (int) (4 * menuDensity);
        slideThumb = new TextView(activity);
        slideThumb.setText("✕");
        slideThumb.setTextColor(Color.WHITE);
        slideThumb.setTextSize(16 * MENU_SCALE);
        slideThumb.setGravity(Gravity.CENTER);

        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumbBg.setColor(Color.parseColor("#E53935"));
        slideThumb.setBackground(thumbBg);

        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        thumbParams.setMargins(thumbMargin, 0, 0, 0);
        slideThumb.setLayoutParams(thumbParams);
        slideTrack.addView(slideThumb);

        slideThumb.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float startX;
            private boolean exitTriggered = false;

            private void triggerExit(View thumbView, float maxX) {
                if (exitTriggered) return;
                exitTriggered = true;
                thumbView.animate().cancel();
                thumbView.setX(maxX);
                slideThumb.setText("✓");
                trackBg.setColor(Color.parseColor("#C62828"));
                trackBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#FF8A80"));
                if (slideLabel != null) {
                    slideLabel.animate().cancel();
                    slideLabel.setText("Closing...");
                    slideLabel.setTypeface(Typeface.DEFAULT_BOLD);
                    slideLabel.setTextColor(Color.WHITE);
                    slideLabel.setAlpha(1.0f);
                }
                if (scrimView != null) {
                    scrimView.setOnClickListener(null);
                    scrimView.setBackgroundColor(Color.parseColor("#E6000000"));
                }
                if (listener != null) {
                    thumbView.postDelayed(listener::onExitRequested, 60);
                }
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (exitTriggered) return true;
                float minX = thumbMargin;
                float maxX = slideTrack.getWidth() - v.getWidth() - thumbMargin;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        startX = v.getX();
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float newX = Math.max(minX, Math.min(startX + deltaX, maxX));
                        v.setX(newX);
                        float progress = (maxX > minX) ? (newX - minX) / (maxX - minX) : 0f;
                        if (progress >= 0.985f) {
                            triggerExit(v, maxX);
                            return true;
                        }
                        if (slideLabel != null) {
                            slideLabel.setAlpha(Math.max(0f, 1.0f - progress * 1.15f));
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float releaseProgress = (maxX > minX) ? (v.getX() - minX) / (maxX - minX) : 0f;
                        if (releaseProgress >= 0.95f) {
                            triggerExit(v, maxX);
                        } else {
                            v.animate().x(minX).setDuration(200).start();
                            if (slideLabel != null) {
                                slideLabel.animate().alpha(1.0f).setDuration(200).start();
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        slideTrack.setVisibility(View.GONE);

        menuCard.addView(controlsRow);
        menuCard.addView(slideTrack);

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

        float density = rootLayout.getResources().getDisplayMetrics().density;
        if (slideThumb != null) {
            slideThumb.setX(4 * density * MENU_SCALE);
        }
        if (slideLabel != null) {
            slideLabel.setAlpha(1.0f);
        }

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
        extraAppsToggleBtn = null;
        extraAppsScrollView = null;
        slideThumb = null;
        slideLabel = null;
    }
}

