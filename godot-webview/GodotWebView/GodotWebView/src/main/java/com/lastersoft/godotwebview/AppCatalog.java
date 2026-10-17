package com.lastersoft.godotwebview;

import java.util.Arrays;
import java.util.List;

/**
 * Catalog of primary chess platforms and categorized extra web applications for ChessBoard.
 */
public final class AppCatalog {

    public static class SiteEntry {
        public final String name;
        public final String url;

        public SiteEntry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class CategoryEntry {
        public final String title;
        public final List<SiteEntry> sites;

        public CategoryEntry(String title, List<SiteEntry> sites) {
            this.title = title;
            this.sites = sites;
        }
    }

    public static final List<SiteEntry> MAIN_SITES = Arrays.asList(
        new SiteEntry("♟  Chess.com", "https://www.chess.com"),
        new SiteEntry("♟  ChessKids.com", "https://www.chesskids.com"),
        new SiteEntry("♟  Lichess", "https://lichess.org/analysis"),
        new SiteEntry("♟  ChessReps", "https://chessreps.com")
    );

    public static final List<CategoryEntry> EXTRA_CATEGORIES = Arrays.asList(
        new CategoryEntry("🎲  Games", Arrays.asList(
            new SiteEntry("🐑  Colonist.io (Catan)", "https://colonist.io"),
            new SiteEntry("✏️  Dots & Boxes", "https://gametable.org/games/dots-and-boxes/"),
            new SiteEntry("🎈  PBS Kids", "https://pbskids.org"),
            new SiteEntry("🐭  ABCmouse", "https://app.abcmouse.com/app/"),
            new SiteEntry("🎨  AutoDraw (Google)", "https://www.autodraw.com"),
            new SiteEntry("🎲  247 Backgammon", "https://www.247backgammon.org"),
            new SiteEntry("🔴  247 Checkers", "https://www.247checkers.com/"),
            new SiteEntry("🌐  247 Games (All)", "https://www.247games.com")
        )),
        new CategoryEntry("📺  Watch", Arrays.asList(
            new SiteEntry("▶️  YouTube", "https://www.youtube.com"),
            new SiteEntry("🦚  Peacock", "https://www.peacocktv.com"),
            new SiteEntry("🦊  Fox One", "https://www.fox.com"),
            new SiteEntry("🏈  CBS Sports", "https://www.cbssports.com/watch"),
            new SiteEntry("⚾  MLB.TV", "https://www.mlb.com/tv"),
            new SiteEntry("🏰  Disney+", "https://www.disneyplus.com"),
            new SiteEntry("📦  Prime Video", "https://www.amazon.com/gp/video/storefront")
        )),
        new CategoryEntry("🌐  Web", Arrays.asList(
            new SiteEntry("🔍  Google", "https://www.google.com"),
            new SiteEntry("🖼️  Google Photos", "https://photos.google.com"),
            new SiteEntry("🗺️  Google Maps", "https://www.google.com/maps"),
            new SiteEntry("👽  Reddit", "https://www.reddit.com"),
            new SiteEntry("📖  Wikipedia", "https://www.wikipedia.org"),
            new SiteEntry("⛅  Weather", "https://weather.com")
        ))
    );

    public static int getTotalExtraAppsCount() {
        int total = 0;
        for (CategoryEntry category : EXTRA_CATEGORIES) {
            total += category.sites.size();
        }
        return total;
    }

    private AppCatalog() {}
}

