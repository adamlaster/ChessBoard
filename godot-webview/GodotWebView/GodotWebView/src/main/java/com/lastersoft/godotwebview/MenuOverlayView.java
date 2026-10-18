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
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.PathParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages two decoupled overlay views for ChessBoard:
 * 1. Fullscreen Launcher: 4 categorized horizontally-scrolling app rows (Primary Chess, Games, Watch, Web),
 *    respecting the right-margin clearance (106dp) so the Board OS button remains accessible.
 * 2. Browser Controls: A slim vertical toolbar overlay docked directly above the live browser,
 *    allowing users to see page zoom, UA changes, reloads, and margin adjustments update live.
 */
public class MenuOverlayView {

    public static final float MENU_SCALE = 2.0f;

    public interface MenuActionListener {
        void onSiteSelected(String url);
        default void onSiteLongPressed(AppCatalog.SiteEntry site) {}
        void onGoBack();
        void onZoomIn();
        void onZoomOut();
        void onRefresh();
        void onToggleDesktopMode(boolean isLongPressSaveDefault);
        default void onToggleRightMargin(boolean isLongPressSaveDefault) {}
        void onRotateScreen(boolean isLongPressResetLandscape);
        void onCloseMenu();
        default void onOpenBrowserControls() {}
        default void onOpenLauncher() {}
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Launcher Views
    private View launcherScrimView;
    private FrameLayout launcherRoot;
    private TextView clockTextView;
    private final List<View> allCardViews = new ArrayList<>();
    private final List<View> protectedHeaderViews = new ArrayList<>();
    private View launcherCloseBtnView;
    private final int[] locationBuffer = new int[2];

    // Browser Controls Views
    private View controlsScrimView;
    private LinearLayout controlsRail;
    private ImageButton backButton;
    private Button desktopModeButton;
    private GradientDrawable desktopModeCircle;
    private ImageButton rightMarginButton;
    private GradientDrawable rightMarginCircle;
    private ControlIconDrawable rightMarginDrawable;

    private boolean isLauncherOpen = false;
    private boolean isControlsOpen = false;
    private int cutoutSize = 0;
    private float density = 1.0f;
    private float menuDensity = 1.0f;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (isLauncherOpen && clockTextView != null) {
                updateClockText();
                handler.postDelayed(this, 30000); // refresh every 30s
            }
        }
    };

    private void updateClockText() {
        if (clockTextView != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            clockTextView.setText(sdf.format(new Date()));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setup(Activity activity, FrameLayout rootLayout, UserAgentManager uaManager, MenuActionListener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        float menuDensity = density * MENU_SCALE;
        this.density = density;
        this.menuDensity = menuDensity;
        this.cutoutSize = (int) (74 * density); // ~111px cutout matching Board OS menu button (x=1828..1907, y=16..95)
        int railWidth = (int) (38 * menuDensity);
        int controlBtnSize = (int) (27 * menuDensity);
        int ctrlSpacing = (int) (3 * menuDensity);
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

        allCardViews.clear();
        protectedHeaderViews.clear();

        // =====================================================================
        // PART 1: FULLSCREEN APP LAUNCHER (4 CATEGORY ROWS)
        // =====================================================================

        // 1A. Scrim for Launcher (outside tap dismiss, passthrough in cutout)
        launcherScrimView = new View(activity);
        launcherScrimView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        launcherScrimView.setBackgroundColor(Color.parseColor("#59000000")); // 35% black
        launcherScrimView.setVisibility(View.GONE);
        launcherScrimView.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            // Let touch fall through to Board OS button inside top-right cutout
            if (x >= v.getWidth() - cutoutSize && y <= cutoutSize) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (listener != null) listener.onCloseMenu();
            }
            return true;
        });

        // 1B. Launcher Root Container (fullscreen FrameLayout with hardware cutout notch in top right)
        launcherRoot = new FrameLayout(activity) {
            private float downX, downY;
            private boolean isDrag = false;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                float rawX = ev.getRawX();
                float rawY = ev.getRawY();

                // Let touches inside top-right cutout fall directly to Board OS menu button!
                if (rawX >= getWidth() - cutoutSize && rawY <= cutoutSize) {
                    return false;
                }

                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = rawX;
                        downY = rawY;
                        isDrag = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(rawX - downX);
                        float dy = Math.abs(rawY - downY);
                        if (!isDrag && (dx > touchSlop || dy > touchSlop)) {
                            isDrag = true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        boolean nearItem = isTapNearAnyItem(downX, downY) || isTapNearAnyItem(rawX, rawY);
                        if (!isDrag && !nearItem) {
                            // Tap in deadspace detected! Close the launcher with haptic feedback.
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            if (listener != null) {
                                listener.onCloseMenu();
                            }
                            MotionEvent cancelEvent = MotionEvent.obtain(ev);
                            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancelEvent);
                            cancelEvent.recycle();
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        isDrag = false;
                        break;
                }

                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                if (rawX >= getWidth() - cutoutSize && rawY <= cutoutSize) {
                    return false;
                }
                return true; // Consume unhandled touches inside launcher so we receive ACTION_UP
            }
        };

        Drawable cutoutBg = new CutoutBackgroundDrawable(
                Color.parseColor("#F50C0E16"), // 96% deep dark slate
                Color.parseColor("#222838"),   // border
                1 * menuDensity,
                14 * menuDensity,             // fillet corner radius
                cutoutSize,
                cutoutSize
        );
        launcherRoot.setBackground(cutoutBg);
        FrameLayout.LayoutParams launcherParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        launcherRoot.setLayoutParams(launcherParams);
        launcherRoot.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            launcherRoot.setElevation(16 * menuDensity);
        }

        // Inner vertical container for launcher content
        LinearLayout launcherContent = new LinearLayout(activity);
        launcherContent.setOrientation(LinearLayout.VERTICAL);
        int padL = (int) (14 * menuDensity);
        int padR = (int) (14 * menuDensity);
        int padT = (int) (8 * menuDensity);
        int padB = (int) (8 * menuDensity);
        launcherContent.setPadding(padL, padT, padR, padB);
        launcherContent.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Header Bar (reserves cutout space on right so buttons never collide with cutout)
        LinearLayout headerBar = new LinearLayout(activity);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setPadding(0, 0, cutoutSize, 0);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (34 * menuDensity));
        headerBar.setLayoutParams(headerParams);

        // Brand Title
        TextView brandTitle = new TextView(activity);
        brandTitle.setText("CHESSBOARD");
        brandTitle.setTextColor(Color.parseColor("#748FFC"));
        brandTitle.setTextSize(14.5f * MENU_SCALE);
        brandTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerBar.addView(brandTitle);
        protectedHeaderViews.add(brandTitle);

        // Launcher Pill Badge
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setColor(Color.parseColor("#1C2234"));
        badgeBg.setCornerRadius(4 * menuDensity);
        badgeBg.setStroke((int) (1 * menuDensity), Color.parseColor("#3B4868"));

        TextView launcherBadge = new TextView(activity);
        launcherBadge.setText("LAUNCHER");
        launcherBadge.setTextColor(Color.parseColor("#91A7FF"));
        launcherBadge.setTextSize(8.5f * MENU_SCALE);
        launcherBadge.setTypeface(Typeface.DEFAULT_BOLD);
        launcherBadge.setBackground(badgeBg);
        launcherBadge.setPadding((int) (6 * menuDensity), (int) (1.5f * menuDensity),
                (int) (6 * menuDensity), (int) (1.5f * menuDensity));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins((int) (6 * menuDensity), 0, 0, 0);
        launcherBadge.setLayoutParams(badgeParams);
        headerBar.addView(launcherBadge);
        protectedHeaderViews.add(launcherBadge);

        // Switch to Browser Controls Shortcut Button
        GradientDrawable ctrlSwitchBg = new GradientDrawable();
        ctrlSwitchBg.setShape(GradientDrawable.RECTANGLE);
        ctrlSwitchBg.setColor(Color.parseColor("#1C2234"));
        ctrlSwitchBg.setCornerRadius(12 * menuDensity);
        ctrlSwitchBg.setStroke((int) (1 * menuDensity), Color.parseColor("#5C7CFA"));

        Button ctrlSwitchBtn = new Button(activity);
        ctrlSwitchBtn.setText("🎛️ Browser Controls");
        ctrlSwitchBtn.setAllCaps(false);
        ctrlSwitchBtn.setTextColor(Color.parseColor("#91A7FF"));
        ctrlSwitchBtn.setTextSize(9.5f * MENU_SCALE);
        ctrlSwitchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        ctrlSwitchBtn.setBackground(ctrlSwitchBg);
        ctrlSwitchBtn.setPadding((int) (10 * menuDensity), 0, (int) (10 * menuDensity), 0);
        LinearLayout.LayoutParams ctrlSwitchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (26 * menuDensity));
        ctrlSwitchParams.setMargins((int) (12 * menuDensity), 0, 0, 0);
        ctrlSwitchBtn.setLayoutParams(ctrlSwitchParams);
        ctrlSwitchBtn.setOnClickListener(v -> {
            if (listener != null) listener.onOpenBrowserControls();
        });
        headerBar.addView(ctrlSwitchBtn);
        protectedHeaderViews.add(ctrlSwitchBtn);

        // Controls Guide Shortcut Button
        GradientDrawable guideBg = new GradientDrawable();
        guideBg.setShape(GradientDrawable.RECTANGLE);
        guideBg.setColor(Color.parseColor("#1A202E"));
        guideBg.setCornerRadius(12 * menuDensity);
        guideBg.setStroke((int) (1 * menuDensity), Color.parseColor("#334155"));

        Button guideBtn = new Button(activity);
        guideBtn.setText("📖 Controls Guide");
        guideBtn.setAllCaps(false);
        guideBtn.setTextColor(Color.parseColor("#CBD5E1"));
        guideBtn.setTextSize(9.5f * MENU_SCALE);
        guideBtn.setBackground(guideBg);
        guideBtn.setPadding((int) (10 * menuDensity), 0, (int) (10 * menuDensity), 0);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (26 * menuDensity));
        guideParams.setMargins((int) (8 * menuDensity), 0, 0, 0);
        guideBtn.setLayoutParams(guideParams);
        guideBtn.setOnClickListener(v -> {
            HelpDialogHelper.show(activity, menuDensity);
        });
        headerBar.addView(guideBtn);
        protectedHeaderViews.add(guideBtn);

        // Header Spacer
        View headerSpacer = new View(activity);
        headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));
        headerBar.addView(headerSpacer);

        // Clock (displayed on right side of header, comfortably left of Board cutout)
        clockTextView = new TextView(activity);
        clockTextView.setTextColor(Color.parseColor("#64748B"));
        clockTextView.setTextSize(10 * MENU_SCALE);
        clockTextView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.setMargins(0, 0, (int) (12 * menuDensity), 0);
        clockTextView.setLayoutParams(clockParams);
        headerBar.addView(clockTextView);
        protectedHeaderViews.add(clockTextView);
        updateClockText();

        launcherContent.addView(headerBar);

        // Divider
        View divider = new View(activity);
        divider.setBackgroundColor(Color.parseColor("#1A202E"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * menuDensity));
        divParams.setMargins(0, (int) (3 * menuDensity), 0, (int) (3 * menuDensity));
        divider.setLayoutParams(divParams);
        launcherContent.addView(divider);

        // 4 Category Rows
        LinearLayout rowsContainer = new LinearLayout(activity);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        rowsContainer.setLayoutParams(rowsParams);

        int cardWidth = (int) (115 * menuDensity);
        int cardSpacing = (int) (7 * menuDensity);

        for (AppCatalog.CategoryEntry category : AppCatalog.ALL_CATEGORIES) {
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            rowLayoutParams.setMargins(0, (int) (2 * menuDensity), 0, (int) (2 * menuDensity));
            rowLayout.setLayoutParams(rowLayoutParams);

            // Row Header
            LinearLayout catHeader = new LinearLayout(activity);
            catHeader.setOrientation(LinearLayout.HORIZONTAL);
            catHeader.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams catHeadParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            catHeadParams.setMargins(0, 0, 0, (int) (2 * menuDensity));
            catHeader.setLayoutParams(catHeadParams);

            TextView catTitle = new TextView(activity);
            catTitle.setText(category.icon + "  " + category.title);
            catTitle.setTextColor(Color.parseColor("#F1F5F9"));
            catTitle.setTextSize(11 * MENU_SCALE);
            catTitle.setTypeface(Typeface.DEFAULT_BOLD);
            catHeader.addView(catTitle);

            GradientDrawable countBg = new GradientDrawable();
            countBg.setShape(GradientDrawable.RECTANGLE);
            countBg.setColor(Color.parseColor("#1C212E"));
            countBg.setCornerRadius(8 * menuDensity);

            TextView countBadge = new TextView(activity);
            countBadge.setText(category.sites.size() + " apps");
            countBadge.setTextColor(Color.parseColor("#94A3B8"));
            countBadge.setTextSize(8.5f * MENU_SCALE);
            countBadge.setBackground(countBg);
            countBadge.setPadding((int) (5 * menuDensity), (int) (1 * menuDensity),
                    (int) (5 * menuDensity), (int) (1 * menuDensity));
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            countParams.setMargins((int) (6 * menuDensity), 0, 0, 0);
            countBadge.setLayoutParams(countParams);
            catHeader.addView(countBadge);

            rowLayout.addView(catHeader);

            // Horizontal Scroll View for Cards
            HorizontalScrollView hScrollView = new HorizontalScrollView(activity);
            hScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hScrollView.setHorizontalScrollBarEnabled(false);
            hScrollView.setClipToPadding(false);
            LinearLayout.LayoutParams hScrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            hScrollView.setLayoutParams(hScrollParams);

            LinearLayout cardsRow = new LinearLayout(activity);
            cardsRow.setOrientation(LinearLayout.HORIZONTAL);
            cardsRow.setGravity(Gravity.CENTER_VERTICAL);
            cardsRow.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

            for (AppCatalog.SiteEntry site : category.sites) {
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setShape(GradientDrawable.RECTANGLE);
                cardBg.setColor(Color.parseColor("#161923"));
                cardBg.setCornerRadius(9 * menuDensity);
                cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#282F42"));

                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(cardBg);
                int cardPad = (int) (7 * menuDensity);
                card.setPadding(cardPad, cardPad, cardPad, cardPad);

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        cardWidth, ViewGroup.LayoutParams.MATCH_PARENT);
                cardParams.setMargins(0, (int) (1.5f * menuDensity), cardSpacing, (int) (1.5f * menuDensity));
                card.setLayoutParams(cardParams);

                // Top row inside card: Icon + Tag
                LinearLayout cardTopRow = new LinearLayout(activity);
                cardTopRow.setOrientation(LinearLayout.HORIZONTAL);
                cardTopRow.setGravity(Gravity.CENTER_VERTICAL);
                cardTopRow.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                // Icon Box
                GradientDrawable iconBoxBg = new GradientDrawable();
                iconBoxBg.setShape(GradientDrawable.RECTANGLE);
                iconBoxBg.setColor(Color.parseColor("#202534"));
                iconBoxBg.setCornerRadius(5 * menuDensity);

                TextView iconView = new TextView(activity);
                iconView.setText(site.icon);
                iconView.setTextSize(10.5f * MENU_SCALE);
                iconView.setGravity(Gravity.CENTER);
                iconView.setBackground(iconBoxBg);
                int iconBoxSize = (int) (18 * menuDensity);
                iconView.setLayoutParams(new LinearLayout.LayoutParams(iconBoxSize, iconBoxSize));
                cardTopRow.addView(iconView);

                // Spacer
                View cardTopSpacer = new View(activity);
                cardTopSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));
                cardTopRow.addView(cardTopSpacer);

                // Optional Tag Badge
                if (!TextUtils.isEmpty(site.tag)) {
                    GradientDrawable tagBg = new GradientDrawable();
                    tagBg.setShape(GradientDrawable.RECTANGLE);
                    tagBg.setColor(Color.parseColor("#182336"));
                    tagBg.setCornerRadius(4 * menuDensity);

                    TextView tagView = new TextView(activity);
                    tagView.setText(site.tag);
                    tagView.setTextColor(site.tag.equals("DEFAULT")
                            ? Color.parseColor("#38D9A9")
                            : Color.parseColor("#748FFC"));
                    tagView.setTextSize(7.5f * MENU_SCALE);
                    tagView.setTypeface(Typeface.DEFAULT_BOLD);
                    tagView.setBackground(tagBg);
                    tagView.setPadding((int) (4 * menuDensity), (int) (1 * menuDensity),
                            (int) (4 * menuDensity), (int) (1 * menuDensity));
                    cardTopRow.addView(tagView);
                }

                card.addView(cardTopRow);

                // Vertical Space between top and bottom
                View cardMiddleSpacer = new View(activity);
                cardMiddleSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));
                card.addView(cardMiddleSpacer);

                // Bottom Titles: Name + Subtitle
                TextView nameView = new TextView(activity);
                nameView.setText(site.name);
                nameView.setTextColor(Color.WHITE);
                nameView.setTextSize(10 * MENU_SCALE);
                nameView.setTypeface(Typeface.DEFAULT_BOLD);
                nameView.setSingleLine(true);
                nameView.setEllipsize(TextUtils.TruncateAt.END);
                card.addView(nameView);

                if (!TextUtils.isEmpty(site.subtitle)) {
                    TextView subtitleView = new TextView(activity);
                    subtitleView.setText(site.subtitle);
                    subtitleView.setTextColor(Color.parseColor("#94A3B8"));
                    subtitleView.setTextSize(7.5f * MENU_SCALE);
                    subtitleView.setSingleLine(true);
                    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(subtitleView);
                }

                // Interactive touch handling on card
                setupCardTouchBehavior(card, cardBg, site, menuDensity, touchSlop, listener);

                cardsRow.addView(card);
                allCardViews.add(card);
            }

            hScrollView.addView(cardsRow);
            rowLayout.addView(hScrollView);
            rowsContainer.addView(rowLayout);
        }

        launcherContent.addView(rowsContainer);
        launcherRoot.addView(launcherContent);

        // Launcher Close Button (✕) positioned below the Board OS menu button cutout,
        // matching the exact screen coordinates and size of the Browser Controls close button.
        GradientDrawable launcherCloseBg = new GradientDrawable();
        launcherCloseBg.setShape(GradientDrawable.OVAL);
        launcherCloseBg.setColor(Color.parseColor("#38181E"));
        launcherCloseBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#E03131"));

        Button launcherCloseBtn = new Button(activity);
        launcherCloseBtn.setText("✕");
        launcherCloseBtn.setTextColor(Color.parseColor("#FFA8A8"));
        launcherCloseBtn.setTextSize(12 * MENU_SCALE);
        launcherCloseBtn.setTypeface(Typeface.DEFAULT_BOLD);
        launcherCloseBtn.setGravity(Gravity.CENTER);
        launcherCloseBtn.setPadding(0, 0, 0, 0);
        launcherCloseBtn.setIncludeFontPadding(false);
        launcherCloseBtn.setBackground(launcherCloseBg);

        FrameLayout.LayoutParams launcherCloseParams = new FrameLayout.LayoutParams(
                controlBtnSize, controlBtnSize, Gravity.TOP | Gravity.RIGHT);
        launcherCloseParams.topMargin = cutoutSize + (int) (24 * density) + (int) (6 * menuDensity);
        launcherCloseParams.rightMargin = (int) (6 * density) + (railWidth - controlBtnSize) / 2;
        launcherCloseBtn.setLayoutParams(launcherCloseParams);
        launcherCloseBtn.setContentDescription("Close Launcher");
        launcherCloseBtn.setOnClickListener(v -> {
            if (listener != null) listener.onCloseMenu();
        });
        launcherCloseBtnView = launcherCloseBtn;
        launcherRoot.addView(launcherCloseBtn);

        // =====================================================================
        // =====================================================================
        // PART 2: DEDICATED SLIM BROWSER CONTROLS RAIL (LIVE OVERLAY ON BROWSER)
        // =====================================================================

        // 2A. Scrim for Browser Controls (transparent backdrop to dismiss on page tap, passthrough in cutout)
        controlsScrimView = new View(activity);
        controlsScrimView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controlsScrimView.setBackgroundColor(Color.TRANSPARENT);
        controlsScrimView.setVisibility(View.GONE);
        controlsScrimView.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            // Let touch fall through to Board OS button inside top-right cutout
            if (x >= v.getWidth() - cutoutSize && y <= cutoutSize) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (listener != null) listener.onCloseMenu();
            }
            return true;
        });

        // 2B. Controls Rail Container (docked right, below top-right Board button cutout)
        GradientDrawable railBg = new GradientDrawable();
        railBg.setShape(GradientDrawable.RECTANGLE);
        railBg.setColor(Color.parseColor("#F210131E")); // 95% opacity dark slate
        railBg.setCornerRadius(16 * menuDensity);
        railBg.setStroke((int) (1 * menuDensity), Color.parseColor("#262D3E"));

        controlsRail = new LinearLayout(activity);
        controlsRail.setOrientation(LinearLayout.VERTICAL);
        controlsRail.setGravity(Gravity.CENTER_HORIZONTAL);
        controlsRail.setBackground(railBg);
        controlsRail.setOnTouchListener((v, event) -> true); // consume touches inside rail

        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                railWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
        railParams.topMargin = cutoutSize + (int) (24 * density);
        railParams.rightMargin = (int) (6 * density);
        controlsRail.setLayoutParams(railParams);
        controlsRail.setPadding(0, (int) (6 * menuDensity), 0, (int) (6 * menuDensity));
        controlsRail.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            controlsRail.setElevation(18 * menuDensity);
        }

        LinearLayout.LayoutParams ctrlBtnParams = new LinearLayout.LayoutParams(controlBtnSize, controlBtnSize);
        ctrlBtnParams.setMargins(0, 0, 0, ctrlSpacing);

        // 1. Close Button (✕)
        GradientDrawable closeCircle = new GradientDrawable();
        closeCircle.setShape(GradientDrawable.OVAL);
        closeCircle.setColor(Color.parseColor("#38181E"));
        closeCircle.setStroke((int) (1.5f * menuDensity), Color.parseColor("#E03131"));

        Button closeBtn = new Button(activity);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.parseColor("#FFA8A8"));
        closeBtn.setTextSize(12 * MENU_SCALE);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(0, 0, 0, 0);
        closeBtn.setIncludeFontPadding(false);
        closeBtn.setBackground(closeCircle);
        closeBtn.setLayoutParams(ctrlBtnParams);
        closeBtn.setContentDescription("Close Browser Controls");
        closeBtn.setOnClickListener(v -> {
            if (listener != null) listener.onCloseMenu();
        });
        controlsRail.addView(closeBtn);

        // 2. Back
        GradientDrawable backCircle = new GradientDrawable();
        backCircle.setShape(GradientDrawable.OVAL);
        backCircle.setColor(Color.parseColor("#202430"));

        backButton = new ImageButton(activity);
        backButton.setBackground(backCircle);
        backButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.BACK));
        backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setLayoutParams(ctrlBtnParams);
        backButton.setContentDescription("Go Back");
        backButton.setOnClickListener(v -> {
            if (listener != null) listener.onGoBack();
        });
        controlsRail.addView(backButton);

        // 3. Zoom In
        GradientDrawable zoomInCircle = new GradientDrawable();
        zoomInCircle.setShape(GradientDrawable.OVAL);
        zoomInCircle.setColor(Color.parseColor("#202430"));

        ImageButton zoomInButton = new ImageButton(activity);
        zoomInButton.setBackground(zoomInCircle);
        zoomInButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.ZOOM_IN));
        zoomInButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        zoomInButton.setPadding(0, 0, 0, 0);
        zoomInButton.setLayoutParams(ctrlBtnParams);
        zoomInButton.setContentDescription("Zoom In");
        zoomInButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomIn();
        });
        controlsRail.addView(zoomInButton);

        // 4. Zoom Out
        GradientDrawable zoomOutCircle = new GradientDrawable();
        zoomOutCircle.setShape(GradientDrawable.OVAL);
        zoomOutCircle.setColor(Color.parseColor("#202430"));

        ImageButton zoomOutButton = new ImageButton(activity);
        zoomOutButton.setBackground(zoomOutCircle);
        zoomOutButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.ZOOM_OUT));
        zoomOutButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        zoomOutButton.setPadding(0, 0, 0, 0);
        zoomOutButton.setLayoutParams(ctrlBtnParams);
        zoomOutButton.setContentDescription("Zoom Out");
        zoomOutButton.setOnClickListener(v -> {
            if (listener != null) listener.onZoomOut();
        });
        controlsRail.addView(zoomOutButton);

        // 5. Refresh
        GradientDrawable refreshCircle = new GradientDrawable();
        refreshCircle.setShape(GradientDrawable.OVAL);
        refreshCircle.setColor(Color.parseColor("#202430"));

        ImageButton refreshButton = new ImageButton(activity);
        refreshButton.setBackground(refreshCircle);
        refreshButton.setImageDrawable(new ControlIconDrawable(ControlIconDrawable.Type.REFRESH));
        refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        refreshButton.setPadding(0, 0, 0, 0);
        refreshButton.setLayoutParams(ctrlBtnParams);
        refreshButton.setContentDescription("Refresh Page");
        refreshButton.setOnClickListener(v -> {
            if (listener != null) listener.onRefresh();
        });
        controlsRail.addView(refreshButton);

        // 6. Desktop / Mobile UA Toggle
        desktopModeCircle = new GradientDrawable();
        desktopModeCircle.setShape(GradientDrawable.OVAL);

        desktopModeButton = new Button(activity);
        desktopModeButton.setTextSize(12 * MENU_SCALE);
        desktopModeButton.setPadding(0, 0, 0, 0);
        desktopModeButton.setIncludeFontPadding(false);
        desktopModeButton.setGravity(Gravity.CENTER);
        desktopModeButton.setBackground(desktopModeCircle);
        desktopModeButton.setLayoutParams(ctrlBtnParams);
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
        controlsRail.addView(desktopModeButton);

        // 7. Right Margin Gutter Toggle (◧)
        rightMarginCircle = new GradientDrawable();
        rightMarginCircle.setShape(GradientDrawable.OVAL);
        rightMarginCircle.setColor(Color.parseColor("#202430"));
        rightMarginDrawable = new ControlIconDrawable(ControlIconDrawable.Type.RIGHT_MARGIN);

        rightMarginButton = new ImageButton(activity);
        rightMarginButton.setBackground(rightMarginCircle);
        rightMarginButton.setImageDrawable(rightMarginDrawable);
        rightMarginButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rightMarginButton.setPadding(0, 0, 0, 0);
        rightMarginButton.setLayoutParams(ctrlBtnParams);
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
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
        controlsRail.addView(rightMarginButton);

        // 8. Screen Rotation (90°)
        GradientDrawable rotateCircle = new GradientDrawable();
        rotateCircle.setShape(GradientDrawable.OVAL);
        rotateCircle.setColor(Color.parseColor("#202430"));

        Button rotateButton = new Button(activity);
        rotateButton.setText("90°");
        rotateButton.setTypeface(Typeface.DEFAULT_BOLD);
        rotateButton.setTextColor(Color.WHITE);
        rotateButton.setTextSize(10 * MENU_SCALE);
        rotateButton.setPadding(0, 0, 0, 0);
        rotateButton.setIncludeFontPadding(false);
        rotateButton.setGravity(Gravity.CENTER);
        rotateButton.setBackground(rotateCircle);
        rotateButton.setLayoutParams(ctrlBtnParams);
        rotateButton.setContentDescription("Rotate Screen");
        rotateButton.setOnClickListener(v -> {
            if (listener != null) listener.onRotateScreen(false);
        });
        rotateButton.setOnLongClickListener(v -> {
            if (listener != null) listener.onRotateScreen(true);
            return true;
        });
        controlsRail.addView(rotateButton);

        // 8. Launcher Shortcut Button (Pawn icon)
        GradientDrawable launcherSwitchCircle = new GradientDrawable();
        launcherSwitchCircle.setShape(GradientDrawable.OVAL);
        launcherSwitchCircle.setColor(Color.parseColor("#1C2234"));
        launcherSwitchCircle.setStroke((int) (1.5f * menuDensity), Color.parseColor("#5C7CFA"));

        Button launcherSwitchBtn = new Button(activity);
        launcherSwitchBtn.setText("♟");
        launcherSwitchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        launcherSwitchBtn.setTextColor(Color.parseColor("#91A7FF"));
        launcherSwitchBtn.setTextSize(12 * MENU_SCALE);
        launcherSwitchBtn.setPadding(0, 0, 0, 0);
        launcherSwitchBtn.setIncludeFontPadding(false);
        launcherSwitchBtn.setGravity(Gravity.CENTER);
        launcherSwitchBtn.setBackground(launcherSwitchCircle);
        launcherSwitchBtn.setLayoutParams(new LinearLayout.LayoutParams(controlBtnSize, controlBtnSize));
        launcherSwitchBtn.setContentDescription("Open Launcher");
        launcherSwitchBtn.setOnClickListener(v -> {
            if (listener != null) listener.onOpenLauncher();
        });
        controlsRail.addView(launcherSwitchBtn);

        // Add views to root hierarchy
        rootLayout.addView(launcherScrimView);
        rootLayout.addView(launcherRoot);
        rootLayout.addView(controlsScrimView);
        rootLayout.addView(controlsRail);
    }

    private View.OnTouchListener createOutsideDismissListener(int touchSlop, MenuActionListener listener) {
        return new View.OnTouchListener() {
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
                        float dx = Math.abs(event.getX() - downX);
                        float dy = Math.abs(event.getY() - downY);
                        if (!isDrag && (dx > touchSlop || dy > touchSlop)) {
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
        };
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCardTouchBehavior(View card, GradientDrawable cardBg, AppCatalog.SiteEntry site,
                                       float menuDensity, int touchSlop, MenuActionListener listener) {
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean isDrag = false;
            private boolean longPressTriggered = false;
            private final Runnable longPressRunnable = () -> {
                longPressTriggered = true;
                card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                cardBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#FAB005"));
                cardBg.setColor(Color.parseColor("#2A261C"));
                card.invalidate();
                if (listener != null) {
                    listener.onSiteLongPressed(site);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        isDrag = false;
                        longPressTriggered = false;
                        cardBg.setStroke((int) (1.5f * menuDensity), Color.parseColor("#5C7CFA"));
                        cardBg.setColor(Color.parseColor("#22283A"));
                        v.invalidate();
                        handler.postDelayed(longPressRunnable, 500);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getX() - downX);
                        float dy = Math.abs(event.getY() - downY);
                        if (!isDrag && (dx > touchSlop || dy > touchSlop)) {
                            isDrag = true;
                            handler.removeCallbacks(longPressRunnable);
                            cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#282F42"));
                            cardBg.setColor(Color.parseColor("#161923"));
                            v.invalidate();
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPressRunnable);
                        cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#282F42"));
                        cardBg.setColor(Color.parseColor("#161923"));
                        v.invalidate();
                        if (!isDrag && !longPressTriggered) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            if (listener != null) {
                                listener.onSiteSelected(site.url);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        isDrag = false;
                        handler.removeCallbacks(longPressRunnable);
                        cardBg.setStroke((int) (1 * menuDensity), Color.parseColor("#282F42"));
                        cardBg.setColor(Color.parseColor("#161923"));
                        v.invalidate();
                        return true;
                }
                return false;
            }
        });
    }

    private boolean isPointNearView(View view, float rawX, float rawY, int buffer) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isAttachedToWindow()) {
            return false;
        }
        view.getLocationOnScreen(locationBuffer);
        int left = locationBuffer[0] - buffer;
        int top = locationBuffer[1] - buffer;
        int right = locationBuffer[0] + view.getWidth() + buffer;
        int bottom = locationBuffer[1] + view.getHeight() + buffer;

        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom;
    }

    private boolean isTapNearAnyItem(float rawX, float rawY) {
        // 1. Board OS menu button cutout notch
        if (launcherRoot != null) {
            int w = launcherRoot.getWidth();
            if (rawX >= w - cutoutSize && rawY <= cutoutSize) {
                return true;
            }
        }

        // 2. Launcher close button directly (buffer = 0 so clicks inside trigger button click, near clicks close via deadspace)
        if (isPointNearView(launcherCloseBtnView, rawX, rawY, 0)) {
            return true;
        }

        // 3. Protected header buttons (buffer = 12 * density ~ 18px)
        int headerBuffer = (int) (12 * density);
        for (int i = 0; i < protectedHeaderViews.size(); i++) {
            if (isPointNearView(protectedHeaderViews.get(i), rawX, rawY, headerBuffer)) {
                return true;
            }
        }

        // 4. All app cards (buffer = 12 * menuDensity ~ 36px)
        // Bridges the 21px inter-card gap and protects adjacent near-misses
        int cardBuffer = (int) (12 * menuDensity);
        for (int i = 0; i < allCardViews.size(); i++) {
            if (isPointNearView(allCardViews.get(i), rawX, rawY, cardBuffer)) {
                return true;
            }
        }

        return false;
    }

    public View getScrimView() {
        return launcherScrimView;
    }

    public View getMenuCard() {
        return isControlsOpen ? controlsRail : launcherRoot;
    }

    public boolean isMenuOpen() {
        return isLauncherOpen || isControlsOpen;
    }

    public boolean isLauncherOpen() {
        return isLauncherOpen;
    }

    public boolean isControlsOpen() {
        return isControlsOpen;
    }

    /**
     * Opens the fullscreen app launcher.
     */
    public void showLauncher(FrameLayout rootLayout) {
        if (launcherRoot == null || rootLayout == null) return;
        hideBrowserControls();

        isLauncherOpen = true;
        updateClockText();
        handler.post(clockRunnable);

        if (launcherScrimView != null) {
            launcherScrimView.setVisibility(View.VISIBLE);
            launcherScrimView.bringToFront();
        }
        launcherRoot.setVisibility(View.VISIBLE);
        launcherRoot.bringToFront();
    }

    /**
     * Opens the slim vertical browser controls rail docked directly over the live browser.
     */
    public void showBrowserControls(FrameLayout rootLayout, boolean canGoBack) {
        if (controlsRail == null || rootLayout == null) return;
        hideLauncher();

        isControlsOpen = true;
        updateBackButtonState(canGoBack);

        if (controlsScrimView != null) {
            controlsScrimView.setVisibility(View.VISIBLE);
            controlsScrimView.bringToFront();
        }
        controlsRail.setVisibility(View.VISIBLE);
        controlsRail.bringToFront();
    }

    /**
     * Backward-compatible open method (defaults to opening Launcher).
     */
    public void showMenu(FrameLayout rootLayout, boolean canGoBack) {
        showLauncher(rootLayout);
    }

    public void hideLauncher() {
        if (!isLauncherOpen) return;
        isLauncherOpen = false;
        handler.removeCallbacks(clockRunnable);
        if (launcherRoot != null) launcherRoot.setVisibility(View.GONE);
        if (launcherScrimView != null) launcherScrimView.setVisibility(View.GONE);
    }

    public void hideBrowserControls() {
        if (!isControlsOpen) return;
        isControlsOpen = false;
        if (controlsRail != null) controlsRail.setVisibility(View.GONE);
        if (controlsScrimView != null) controlsScrimView.setVisibility(View.GONE);
    }

    public void hideMenu() {
        hideLauncher();
        hideBrowserControls();
    }

    public void repositionMenuCard(ViewGroup rootLayout) {
        if (launcherRoot != null) launcherRoot.requestLayout();
        if (controlsRail != null) controlsRail.requestLayout();
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
            rightMarginCircle.setColor(Color.parseColor("#202430"));
            rightMarginCircle.setStroke(0, Color.TRANSPARENT);
            if (rightMarginDrawable != null) {
                rightMarginDrawable.setIconColor(Color.parseColor("#CBD5E1"));
            }
        }
    }

    public void bringToFront() {
        if (launcherScrimView != null && isLauncherOpen) launcherScrimView.bringToFront();
        if (launcherRoot != null && isLauncherOpen) launcherRoot.bringToFront();
        if (controlsScrimView != null && isControlsOpen) controlsScrimView.bringToFront();
        if (controlsRail != null && isControlsOpen) controlsRail.bringToFront();
    }

    public void destroy(FrameLayout rootLayout) {
        hideMenu();
        if (launcherScrimView != null && rootLayout != null) {
            rootLayout.removeView(launcherScrimView);
            launcherScrimView = null;
        }
        if (launcherRoot != null && rootLayout != null) {
            rootLayout.removeView(launcherRoot);
            launcherRoot = null;
        }
        if (controlsScrimView != null && rootLayout != null) {
            rootLayout.removeView(controlsScrimView);
            controlsScrimView = null;
        }
        if (controlsRail != null && rootLayout != null) {
            rootLayout.removeView(controlsRail);
            controlsRail = null;
        }
        allCardViews.clear();
        protectedHeaderViews.clear();
        launcherCloseBtnView = null;
        clockTextView = null;
        backButton = null;
        desktopModeButton = null;
        desktopModeCircle = null;
        rightMarginButton = null;
        rightMarginCircle = null;
        rightMarginDrawable = null;
    }

    /**
     * Renders mathematically and optically centered Material Design vector icons
     * (Back, Zoom In, Zoom Out, Refresh, Help) with unified stroke weight,
     * crisp scaling, and identical visual geometry.
     */
    public static class ControlIconDrawable extends Drawable {
        public enum Type {
            BACK("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"),
            ZOOM_OUT("M19 13H5v-2h14v2z"),
            ZOOM_IN("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
            REFRESH("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"),
            RIGHT_MARGIN("M16 20H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2h12c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2zm0-14H4v12h12V6zM20 4h2v16h-2z"),
            HELP("M11.07 12.85c.77-1.39 2.25-2.21 3.11-3.44.91-1.29.4-4.05-2.18-4.05-1.9 0-2.67 1.48-2.67 2.68H7.1C7.1 4.7 9.33 3 12 3c2.65 0 4.22 1.72 4.22 4.07 0 1.94-1.22 3.03-2.14 3.9-.92.88-1.57 1.49-1.89 2.45-.19.57-.22 1.1-.22 2.48h-2.1c0-1.82-.03-2.61.28-3.05zM11 18h2v2h-2z");

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

    /**
     * Renders a dark fullscreen card background featuring an inverted fillet cutout
     * notch in the upper-right corner so the Board OS floating menu button remains
     * completely unobstructed and interactive.
     */
    public static class CutoutBackgroundDrawable extends Drawable {
        private final Paint fillPaint;
        private final Paint strokePaint;
        private final float strokeWidth;
        private final float cornerRadius;
        private final int cutoutWidth;
        private final int cutoutHeight;
        private final Path path = new Path();

        public CutoutBackgroundDrawable(int fillColor, int strokeColor, float strokeWidth,
                                        float cornerRadius, int cutoutWidth, int cutoutHeight) {
            this.fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.fillPaint.setStyle(Paint.Style.FILL);
            this.fillPaint.setColor(fillColor);

            this.strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.strokePaint.setStyle(Paint.Style.STROKE);
            this.strokePaint.setColor(strokeColor);
            this.strokePaint.setStrokeWidth(strokeWidth);

            this.strokeWidth = strokeWidth;
            this.cornerRadius = cornerRadius;
            this.cutoutWidth = cutoutWidth;
            this.cutoutHeight = cutoutHeight;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rebuildPath(bounds);
        }

        private void rebuildPath(Rect bounds) {
            path.reset();
            int w = bounds.width();
            int h = bounds.height();
            if (w <= 0 || h <= 0) return;

            int cw = Math.min(cutoutWidth, w);
            int ch = Math.min(cutoutHeight, h);
            float r = Math.min(cornerRadius, Math.min(cw / 3.0f, ch / 3.0f));

            path.moveTo(0, 0);

            if (cw > 0 && ch > 0) {
                // 1. Top edge running towards cutout
                path.lineTo(w - cw - r, 0);
                // 2. Arc 1: top edge curving down into vertical wall of cutout
                path.arcTo(new RectF(w - cw - 2 * r, 0, w - cw, 2 * r), 270f, 90f);
                // 3. Vertical cutout left wall running down
                path.lineTo(w - cw, ch - r);
                // 4. Arc 2: bottom-left corner of cutout turning right
                path.arcTo(new RectF(w - cw, ch - 2 * r, w - cw + 2 * r, ch), 180f, -90f);
                // 5. Horizontal cutout bottom wall running to right edge
                path.lineTo(w - r, ch);
                // 6. Arc 3: bottom wall curving down into right screen edge
                path.arcTo(new RectF(w - 2 * r, ch, w, ch + 2 * r), 270f, 90f);
            } else {
                path.lineTo(w, 0);
            }

            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            if (path.isEmpty()) {
                rebuildPath(getBounds());
            }
            canvas.drawPath(path, fillPaint);
            if (strokeWidth > 0) {
                canvas.drawPath(path, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
