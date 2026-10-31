package com.lastersoft.godotwebview;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catalog of primary chess platforms and categorized extra web applications for ChessBoard.
 */
public final class AppCatalog {

    public static class SiteEntry {
        public final String name;
        public final String url;
        public final String secondaryUrl;
        public final String icon;
        public final String subtitle;
        public final String tag;

        public SiteEntry(String name, String url) {
            this(name, url, null, "🌐", "", "");
        }

        public SiteEntry(String name, String url, String icon, String subtitle) {
            this(name, url, null, icon, subtitle, "");
        }

        public SiteEntry(String name, String url, String icon, String subtitle, String tag) {
            this(name, url, null, icon, subtitle, tag);
        }

        public SiteEntry(String name, String url, String secondaryUrl, String icon, String subtitle, String tag) {
            this.name = name;
            this.url = url;
            this.secondaryUrl = secondaryUrl;
            this.icon = icon;
            this.subtitle = subtitle;
            this.tag = tag;
        }

        public boolean isDuo() {
            return secondaryUrl != null && !secondaryUrl.trim().isEmpty();
        }
    }

    public static class CategoryEntry {
        public final String title;
        public final String icon;
        public final List<SiteEntry> sites;

        public CategoryEntry(String title, List<SiteEntry> sites) {
            this(title, "♟", sites);
        }

        public CategoryEntry(String title, String icon, List<SiteEntry> sites) {
            this.title = title;
            this.icon = icon;
            this.sites = sites;
        }
    }

    public static final List<SiteEntry> MAIN_SITES = Arrays.asList(
        new SiteEntry("Chess.com", "https://www.chess.com", "♟", "Play & Puzzles", "DEFAULT"),
        new SiteEntry("Lichess", "https://lichess.org/analysis", "♞", "Analysis & Play", "OPEN SOURCE"),
        new SiteEntry("ChessKids", "https://www.chesskid.com", "👑", "Lessons & Games", "KIDS"),
        new SiteEntry("ChessReps", "https://chessreps.com", "🎯", "Repertoire Training", "TRAINER")
    );

    public static final List<SiteEntry> DEFAULT_DUOS = Arrays.asList(
        new SiteEntry("Chess + YouTube", "https://www.chess.com", "https://www.youtube.com", "♟️▶️", "Play & Watch", "DUO"),
        new SiteEntry("ChessKids + YouTube", "https://www.chesskid.com", "https://www.youtube.com", "🧒▶️", "Kids & Watch", "DUO"),
        new SiteEntry("Chess + Apple TV", "https://www.chess.com", "https://tv.apple.com", "♟️🍎", "Play & Stream", "DUO"),
        new SiteEntry("Lichess + Twitch", "https://lichess.org/analysis", "https://www.twitch.tv", "♞🟣", "Analysis & Stream", "DUO"),
        new SiteEntry("Chess + Reddit", "https://www.chess.com", "https://www.reddit.com/r/chess", "♟️👽", "Play & Community", "DUO"),
        new SiteEntry("Colonist + YouTube", "https://colonist.io", "https://www.youtube.com", "🐑▶️", "Catan & Stream", "DUO")
    );

    public static final List<CategoryEntry> ALL_CATEGORIES = Arrays.asList(
        new CategoryEntry("Primary Chess", "♟", MAIN_SITES),
        new CategoryEntry("Tabletop Duos", "◫", DEFAULT_DUOS),
        new CategoryEntry("Games", "🎲", Arrays.asList(
            new SiteEntry("AutoDraw", "https://www.autodraw.com", "🎨", "Google AI Drawing", "AI"),
            new SiteEntry("DJ Turntables", "https://buttonbass.com/DJ.html", "🎧", "Mix & Scratch Decks", "DJ"),
            new SiteEntry("Patatap", "https://patatap.com", "🎹", "Touch Sound & Visuals", "AUDIO"),
            new SiteEntry("Colonist.io", "https://colonist.io", "🐑", "Catan Board Game", "CATAN"),
            new SiteEntry("ABCmouse", "https://app.abcmouse.com/app/", "🐭", "Early Learning & Games", "KIDS"),
            new SiteEntry("Dots & Boxes", "https://gametable.org/games/dots-and-boxes/", "✏️", "Classic Grid Game"),
            new SiteEntry("PBS Kids", "https://pbskids.org", "🎈", "Interactive Games", "KIDS"),
            new SiteEntry("247 Backgammon", "https://www.247backgammon.org", "🎲", "247 Games"),
            new SiteEntry("247 Checkers", "https://www.247checkers.com/", "🔴", "247 Games"),
            new SiteEntry("247 Games (All)", "https://www.247games.com", "🌐", "Full Catalog")
        )),
        new CategoryEntry("Watch", "📺", Arrays.asList(
            new SiteEntry("YouTube", "https://www.youtube.com", "▶️", "Videos & Streaming", "MEDIA"),
            new SiteEntry("CBS Sports", "https://www.cbssports.com/watch", "🏈", "Live Games & Watch", "SPORTS"),
            new SiteEntry("Peacock", "https://www.peacocktv.com", "🦚", "NBC Sports & TV", "TV"),
            new SiteEntry("MLB.TV", "https://www.mlb.com/tv", "⚾", "Baseball Stream", "SPORTS"),
            new SiteEntry("Disney+", "https://www.disneyplus.com", "🏰", "Movies & Shows", "MOVIES"),
            new SiteEntry("Prime Video", "https://www.amazon.com/gp/video/storefront", "📦", "Amazon Prime", "VIDEO"),
            new SiteEntry("Fox One", "https://www.fox.com", "🦊", "Live TV & Sports", "TV"),
            new SiteEntry("Fandango", "https://athome.fandango.com/", "🎟️", "Movies & TV", "MOVIES"),
            new SiteEntry("Apple TV", "https://tv.apple.com", "🍎", "Originals & Movies", "TV")
        )),
        new CategoryEntry("Web", "🌐", Arrays.asList(
            new SiteEntry("Google", "https://www.google.com", "🔍", "Web Search", "SEARCH"),
            new SiteEntry("Google Photos", "https://photos.google.com", "🖼️", "Cloud Gallery", "PHOTOS"),
            new SiteEntry("Google Maps", "https://www.google.com/maps", "🗺️", "Explore & Navigation", "MAPS"),
            new SiteEntry("Reddit", "https://www.reddit.com", "👽", "Discussions & Chess", "COMMUNITY"),
            new SiteEntry("Wikipedia", "https://www.wikipedia.org", "📖", "Encyclopedia", "INFO"),
            new SiteEntry("Weather", "https://weather.com", "⛅", "Forecast & Radar", "LOCAL")
        ))
    );

    public static final List<CategoryEntry> EXTRA_CATEGORIES = ALL_CATEGORIES.subList(1, ALL_CATEGORIES.size());

    public static final String PREF_CUSTOM_DUOS = "custom_tabletop_duos";

    /**
     * Retrieves the active list of Tabletop Duos from SharedPreferences, falling back to DEFAULT_DUOS.
     */
    public static List<SiteEntry> getDuos(Context context) {
        if (context == null) return new ArrayList<>(DEFAULT_DUOS);
        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(PREF_CUSTOM_DUOS, null);
        if (raw == null || raw.trim().isEmpty()) {
            SharedPreferences legacyPrefs = context.getSharedPreferences(UserAgentManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            raw = legacyPrefs.getString(PREF_CUSTOM_DUOS, null);
        }
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>(DEFAULT_DUOS);
        }

        try {
            JSONArray array = new JSONArray(raw);
            List<SiteEntry> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.getString("name");
                String primaryUrl = obj.getString("primaryUrl");
                String secondaryUrl = obj.getString("secondaryUrl");
                String icon = obj.optString("icon", "◫");
                String subtitle = obj.optString("subtitle", "Custom Duo");
                list.add(new SiteEntry(name, primaryUrl, secondaryUrl, icon, subtitle, "DUO"));
            }
            return list.isEmpty() ? new ArrayList<>(DEFAULT_DUOS) : list;
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_DUOS);
        }
    }

    /**
     * Persists the list of Tabletop Duos into SharedPreferences.
     */
    public static void saveDuos(Context context, List<SiteEntry> duos) {
        if (context == null || duos == null) return;
        try {
            JSONArray array = new JSONArray();
            for (SiteEntry site : duos) {
                JSONObject obj = new JSONObject();
                obj.put("name", site.name);
                obj.put("primaryUrl", site.url);
                obj.put("secondaryUrl", site.secondaryUrl);
                obj.put("icon", site.icon != null ? site.icon : "◫");
                obj.put("subtitle", site.subtitle != null ? site.subtitle : "Custom Duo");
                array.put(obj);
            }
            SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_CUSTOM_DUOS, array.toString()).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Adds a new Duo or updates an existing Duo with the same name.
     */
    public static boolean addDuo(Context context, SiteEntry duo) {
        if (context == null || duo == null) return false;
        List<SiteEntry> current = getDuos(context);
        boolean updated = false;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).name.equalsIgnoreCase(duo.name)) {
                current.set(i, duo);
                updated = true;
                break;
            }
        }
        if (!updated) {
            current.add(duo);
        }
        saveDuos(context, current);
        return true;
    }

    /**
     * Removes a Duo by name from persistent storage.
     */
    public static boolean removeDuo(Context context, String duoName) {
        if (context == null || duoName == null || duoName.trim().isEmpty()) return false;
        List<SiteEntry> current = getDuos(context);
        boolean removed = false;
        for (int i = current.size() - 1; i >= 0; i--) {
            if (current.get(i).name.equalsIgnoreCase(duoName.trim())) {
                current.remove(i);
                removed = true;
            }
        }
        if (removed) {
            saveDuos(context, current);
        }
        return removed;
    }

    /**
     * Resets Tabletop Duos back to the default factory presets.
     */
    public static void resetDuos(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(PREF_CUSTOM_DUOS)
                .remove(PREF_CAT_SITES_PREFIX + "Tabletop Duos")
                .apply();
    }

    public static int getTotalExtraAppsCount() {
        int total = 0;
        for (CategoryEntry category : EXTRA_CATEGORIES) {
            total += category.sites.size();
        }
        return total;
    }

    public static final String PREF_ROW_ORDER = "launcher_row_order";

    /**
     * Returns the categories in user-customized order, falling back to default ALL_CATEGORIES.
     * Dynamically loads custom Duos for the Tabletop Duos row.
     * Automatically appends any newly added categories that were not present in saved order.
     */
    public static List<CategoryEntry> getOrderedCategories(Context context) {
        if (context == null) return ALL_CATEGORIES;
        List<SiteEntry> activeDuos = getDuos(context);

        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        String rawOrder = prefs.getString(PREF_ROW_ORDER, null);
        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            SharedPreferences legacyPrefs = context.getSharedPreferences(UserAgentManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            rawOrder = legacyPrefs.getString(PREF_ROW_ORDER, null);
        }

        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            List<CategoryEntry> defaults = new ArrayList<>();
            for (CategoryEntry cat : ALL_CATEGORIES) {
                if (cat.title.equalsIgnoreCase("Tabletop Duos")) {
                    defaults.add(new CategoryEntry(cat.title, cat.icon, activeDuos));
                } else {
                    defaults.add(cat);
                }
            }
            return defaults;
        }

        String[] titles = rawOrder.split(",");
        List<CategoryEntry> result = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (String title : titles) {
            String clean = title.trim();
            for (CategoryEntry cat : ALL_CATEGORIES) {
                if (cat.title.equalsIgnoreCase(clean) && !added.contains(cat.title)) {
                    if (cat.title.equalsIgnoreCase("Tabletop Duos")) {
                        result.add(new CategoryEntry(cat.title, cat.icon, activeDuos));
                    } else {
                        result.add(cat);
                    }
                    added.add(cat.title);
                    break;
                }
            }
        }

        // Add any categories that were not in the saved list (forward compatibility)
        for (CategoryEntry cat : ALL_CATEGORIES) {
            if (!added.contains(cat.title)) {
                if (cat.title.equalsIgnoreCase("Tabletop Duos")) {
                    result.add(new CategoryEntry(cat.title, cat.icon, activeDuos));
                } else {
                    result.add(cat);
                }
                added.add(cat.title);
            }
        }

        return result;
    }

    /**
     * Persists the ordered category titles in SharedPreferences.
     */
    public static void saveRowOrder(Context context, List<String> order) {
        if (context == null || order == null || order.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i).trim());
        }
        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_ROW_ORDER, sb.toString()).apply();
    }

    public static final String PREF_CAT_SITES_PREFIX = "cat_sites_order_";

    /**
     * Returns the list of SiteEntry items for a category in user-customized order.
     * Automatically appends any newly added sites that were not present in saved order.
     */
    public static List<SiteEntry> getOrderedSitesForCategory(Context context, CategoryEntry category) {
        if (context == null || category == null) {
            return category != null && category.sites != null ? category.sites : new ArrayList<>();
        }
        List<SiteEntry> baseSites = category.title.equalsIgnoreCase("Tabletop Duos")
                ? getDuos(context)
                : (category.sites != null ? category.sites : new ArrayList<>());

        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        String prefKey = PREF_CAT_SITES_PREFIX + category.title;
        String rawOrder = prefs.getString(prefKey, null);
        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            SharedPreferences legacyPrefs = context.getSharedPreferences(UserAgentManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            rawOrder = legacyPrefs.getString(prefKey, null);
        }
        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            return baseSites;
        }

        String[] names = rawOrder.split(",");
        List<SiteEntry> result = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (String name : names) {
            String clean = name.trim();
            for (SiteEntry site : baseSites) {
                if (site.name.equalsIgnoreCase(clean) && !added.contains(site.name)) {
                    result.add(site);
                    added.add(site.name);
                    break;
                }
            }
        }

        // Add any sites that were not in the saved list (e.g. newly added sites/duos)
        for (SiteEntry site : baseSites) {
            if (!added.contains(site.name)) {
                result.add(site);
                added.add(site.name);
            }
        }

        return result;
    }

    /**
     * Persists the ordered site names for a specific category in SharedPreferences.
     */
    public static void saveSitesOrderForCategory(Context context, String categoryTitle, List<String> siteNames) {
        if (context == null || categoryTitle == null || siteNames == null || siteNames.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < siteNames.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(siteNames.get(i).trim());
        }
        SharedPreferences prefs = context.getSharedPreferences(UserAgentManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_CAT_SITES_PREFIX + categoryTitle, sb.toString()).apply();
    }

    public static SiteEntry findSiteEntryByUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        for (CategoryEntry cat : ALL_CATEGORIES) {
            for (SiteEntry site : cat.sites) {
                String siteKey = UserAgentManager.getSiteKey(site.url);
                if (!siteKey.isEmpty() && url.contains(siteKey)) {
                    return site;
                }
            }
        }
        return null;
    }

    private AppCatalog() {}
}
