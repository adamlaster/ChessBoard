package com.lastersoft.godotwebview;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Manages UI color themes for ChessBoard:
 * - MICHIGAN: University of Michigan official Maize (#FFCB05) and Blue (#00274C)
 * - PISTONS: Detroit Pistons Royal Blue (#1D42BA) and Red (#ED174C)
 * - LIONS: Detroit Lions Honolulu Blue (#0076B6) and Silver (#00A3E0 / #B0B7BC)
 * - TIGERS: Detroit Tigers Midnight Navy (#0C2340) and Tiger Orange (#FA4616)
 * - UNICORNS: Utica Unicorns Purple (#5A2D81) and Red (#ED1C24)
 * - CLASSIC: Classic Slate Charcoal (#121622) with Indigo (#4C6EF5) and Cyan accents
 *
 * Persists user preference in SharedPreferences and provides theme tokens for Launcher,
 * Browser Controls Rail, Split-Screen Divider, Drag Overlay, and Controls Bar.
 */
public final class ThemeManager {

    public static final String PREF_THEME = "app_theme_id";

    public enum Theme {
        MICHIGAN(
            "michigan",
            "University of Michigan",
            "〽️ Maize & Blue",
            "〽️ Theme: U of M",
            "〽️ M",
            "#001226", // Launcher backdrop (Deepest Navy)
            "#00274C", // Michigan Blue (Primary brand)
            "#FFCB05", // Michigan Maize (Primary accent / Gold)
            "#08182E", // Card background (Rich Dark Navy)
            "#143156", // Card unselected border
            "#FFCB05", // Card active border (Maize)
            "#00274C", // Badge background (Michigan Blue)
            "#FFCB05", // Badge text / border (Maize)
            "#F2001B36", // Controls rail background (95% Michigan Navy)
            "#FFCB05", // Controls rail border (Maize)
            "#081B30", // Controls button background
            "#FFCB05", // Controls active accent (Maize)
            "#001428", // Divider bar background
            "#FFCB05", // Divider handle (Maize)
            "#F8000F22", // Drag overlay curtain (Deep obsidian navy)
            "#08182E", // Drag card background
            "#FFCB05", // Drag card border (Maize)
            "#F0001B36"  // Split control bar background
        ),
        PISTONS(
            "pistons",
            "Detroit Pistons",
            "🏀 Royal Blue & Red",
            "🏀 Theme: Pistons",
            "🏀 P",
            "#060C1C", // Launcher backdrop (Midnight Royal Navy)
            "#1D42BA", // Pistons Royal Blue (Primary brand)
            "#ED174C", // Pistons Vibrant Red (Primary accent)
            "#0B1630", // Card background (Pistons navy surface)
            "#192D5A", // Card unselected border
            "#ED174C", // Card active border (Red)
            "#1D42BA", // Badge background (Royal Blue)
            "#FFFFFF", // Badge text / border
            "#F2081328", // Controls rail background
            "#ED174C", // Controls rail border (Red)
            "#0F2145", // Controls button background
            "#ED174C", // Controls active accent (Red)
            "#081024", // Divider bar background
            "#ED174C", // Divider handle (Red)
            "#F8060C1C", // Drag overlay curtain
            "#0B1630", // Drag card background
            "#ED174C", // Drag card border (Red)
            "#F0081328"  // Split control bar background
        ),
        LIONS(
            "lions",
            "Detroit Lions",
            "🦁 Honolulu Blue & Silver",
            "🦁 Theme: Lions",
            "🦁 L",
            "#030E19", // Launcher backdrop (Honolulu Deep Navy)
            "#0076B6", // Honolulu Blue (Primary brand)
            "#00A3E0", // Vibrant Honolulu Cyan-Blue (Primary accent)
            "#07192C", // Card background
            "#123554", // Card unselected border
            "#00A3E0", // Card active border (Honolulu Blue)
            "#0076B6", // Badge background (Honolulu Blue)
            "#F1F5F9", // Badge text / border (Silver White)
            "#F2051322", // Controls rail background
            "#00A3E0", // Controls rail border
            "#0A243E", // Controls button background
            "#00A3E0", // Controls active accent
            "#051525", // Divider bar background
            "#00A3E0", // Divider handle
            "#F8030E19", // Drag overlay curtain
            "#07192C", // Drag card background
            "#00A3E0", // Drag card border
            "#F0051322"  // Split control bar background
        ),
        TIGERS(
            "tigers",
            "Detroit Tigers",
            "🐅 Midnight Navy & Orange",
            "🐅 Theme: Tigers",
            "🐅 D",
            "#020914", // Launcher backdrop (Deep Midnight Navy)
            "#0C2340", // Midnight Navy (Primary brand)
            "#FA4616", // Tiger Orange (Primary accent)
            "#061426", // Card background (Midnight Navy surface)
            "#102A4C", // Card unselected border
            "#FA4616", // Card active border (Orange)
            "#0C2340", // Badge background (Midnight Navy)
            "#FA4616", // Badge text / border (Orange)
            "#F2050F1E", // Controls rail background
            "#FA4616", // Controls rail border (Orange)
            "#0A1E38", // Controls button background
            "#FA4616", // Controls active accent (Orange)
            "#040C1A", // Divider bar background
            "#FA4616", // Divider handle (Orange)
            "#F8020914", // Drag overlay curtain
            "#061426", // Drag card background
            "#FA4616", // Drag card border (Orange)
            "#F0050F1E"  // Split control bar background
        ),
        UNICORNS(
            "unicorns",
            "Utica Unicorns",
            "🦄 Purple & Red",
            "🦄 Theme: Unicorns",
            "🦄 U",
            "#12061A", // Launcher backdrop (Deep Obsidian Plum)
            "#5A2D81", // Unicorn Purple (Primary brand)
            "#ED1C24", // Unicorn Red (Primary accent)
            "#1C0C29", // Card background (Plum dark surface)
            "#3A1752", // Card unselected border
            "#ED1C24", // Card active border (Red)
            "#5A2D81", // Badge background (Unicorn Purple)
            "#FFFFFF", // Badge text / border
            "#F2160924", // Controls rail background
            "#ED1C24", // Controls rail border (Red)
            "#260E38", // Controls button background
            "#ED1C24", // Controls active accent (Red)
            "#150722", // Divider bar background
            "#ED1C24", // Divider handle (Red)
            "#F812061A", // Drag overlay curtain
            "#1C0C29", // Drag card background
            "#ED1C24", // Drag card border (Red)
            "#F0160924"  // Split control bar background
        ),
        CLASSIC(
            "classic",
            "Classic Slate",
            "♟ Obsidian & Indigo",
            "🎨 Theme: Classic",
            "♟ S",
            "#121622", // Launcher backdrop (Deep Obsidian)
            "#1E2538", // Slate Primary
            "#4C6EF5", // Indigo Accent
            "#161B26", // Card background
            "#28324A", // Card unselected border
            "#4C6EF5", // Card active border
            "#1C2234", // Badge background
            "#91A7FF", // Badge text / border
            "#F210131E", // Controls rail background
            "#262D3E", // Controls rail border
            "#202430", // Controls button background
            "#22B8CF", // Controls active accent
            "#151A28", // Divider bar background
            "#4C6EF5", // Divider handle
            "#F80A0D16", // Drag overlay curtain
            "#131722", // Drag card background
            "#22B8CF", // Drag card active border
            "#F0121724"  // Split control bar background
        );

        public final String id;
        public final String displayName;
        public final String subtitle;
        public final String buttonLabel;
        public final String swatchText;
        public final int launcherBackdropColor;
        public final int primaryColor;
        public final int accentColor;
        public final int cardBgColor;
        public final int cardBorderColor;
        public final int cardActiveBorderColor;
        public final int badgeBgColor;
        public final int badgeTextColor;
        public final int controlsRailBgColor;
        public final int controlsRailBorderColor;
        public final int controlsBtnBgColor;
        public final int controlsActiveAccentColor;
        public final int dividerBgColor;
        public final int dividerHandleColor;
        public final int dragOverlayCurtainColor;
        public final int dragCardBgColor;
        public final int dragCardBorderColor;
        public final int splitControlBarBgColor;

        Theme(String id, String displayName, String subtitle, String buttonLabel, String swatchText,
              String launcherBackdropHex, String primaryHex, String accentHex,
              String cardBgHex, String cardBorderHex, String cardActiveBorderHex,
              String badgeBgHex, String badgeTextHex,
              String controlsRailBgHex, String controlsRailBorderHex, String controlsBtnBgHex, String controlsActiveAccentHex,
              String dividerBgHex, String dividerHandleHex,
              String dragOverlayCurtainHex, String dragCardBgHex, String dragCardBorderHex,
              String splitControlBarBgHex) {
            this.id = id;
            this.displayName = displayName;
            this.subtitle = subtitle;
            this.buttonLabel = buttonLabel;
            this.swatchText = swatchText;
            this.launcherBackdropColor = Color.parseColor(launcherBackdropHex);
            this.primaryColor = Color.parseColor(primaryHex);
            this.accentColor = Color.parseColor(accentHex);
            this.cardBgColor = Color.parseColor(cardBgHex);
            this.cardBorderColor = Color.parseColor(cardBorderHex);
            this.cardActiveBorderColor = Color.parseColor(cardActiveBorderHex);
            this.badgeBgColor = Color.parseColor(badgeBgHex);
            this.badgeTextColor = Color.parseColor(badgeTextHex);
            this.controlsRailBgColor = Color.parseColor(controlsRailBgHex);
            this.controlsRailBorderColor = Color.parseColor(controlsRailBorderHex);
            this.controlsBtnBgColor = Color.parseColor(controlsBtnBgHex);
            this.controlsActiveAccentColor = Color.parseColor(controlsActiveAccentHex);
            this.dividerBgColor = Color.parseColor(dividerBgHex);
            this.dividerHandleColor = Color.parseColor(dividerHandleHex);
            this.dragOverlayCurtainColor = Color.parseColor(dragOverlayCurtainHex);
            this.dragCardBgColor = Color.parseColor(dragCardBgHex);
            this.dragCardBorderColor = Color.parseColor(dragCardBorderHex);
            this.splitControlBarBgColor = Color.parseColor(splitControlBarBgHex);
        }
    }

    private static Theme currentTheme = null;

    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }

    public static Theme getActiveTheme(Context context) {
        if (currentTheme != null) return currentTheme;
        if (context == null) return Theme.MICHIGAN;
        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        String savedId = prefs.getString(PREF_THEME, Theme.MICHIGAN.id); // Default to MICHIGAN
        for (Theme t : Theme.values()) {
            if (t.id.equalsIgnoreCase(savedId)) {
                currentTheme = t;
                return t;
            }
        }
        currentTheme = Theme.MICHIGAN;
        return currentTheme;
    }

    public static void setActiveTheme(Context context, Theme theme) {
        if (theme == null) return;
        currentTheme = theme;
        if (context != null) {
            context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_THEME, theme.id)
                    .commit();
        }
    }

    private ThemeManager() {}
}
