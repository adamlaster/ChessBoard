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
        public final String icon;
        public final String subtitle;
        public final String tag;

        public SiteEntry(String name, String url) {
            this(name, url, "🌐", "", "");
        }

        public SiteEntry(String name, String url, String icon, String subtitle) {
            this(name, url, icon, subtitle, "");
        }

        public SiteEntry(String name, String url, String icon, String subtitle, String tag) {
            this.name = name;
            this.url = url;
            this.icon = icon;
            this.subtitle = subtitle;
            this.tag = tag;
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
        new SiteEntry("ChessKids", "https://www.chesskids.com", "👑", "Lessons & Games", "KIDS"),
        new SiteEntry("ChessReps", "https://chessreps.com", "🎯", "Repertoire Training", "TRAINER")
    );

    public static final List<CategoryEntry> ALL_CATEGORIES = Arrays.asList(
        new CategoryEntry("Primary Chess", "♟", MAIN_SITES),
        new CategoryEntry("Games", "🎲", Arrays.asList(
            new SiteEntry("AutoDraw", "https://www.autodraw.com", "🎨", "Google AI Drawing", "AI"),
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
            new SiteEntry("Fox One", "https://www.fox.com", "🦊", "Live TV & Sports", "TV")
        )),
        new CategoryEntry("Web", "🌐", Arrays.asList(
            new SiteEntry("Google", "https://www.google.com", "🔍", "Web Search", "SEARCH"),
            new SiteEntry("Google Photos", "https://photos.google.com/login", "🖼️", "Cloud Gallery", "PHOTOS"),
            new SiteEntry("Google Maps", "https://www.google.com/maps", "🗺️", "Explore & Navigation", "MAPS"),
            new SiteEntry("Reddit", "https://www.reddit.com", "👽", "Discussions & Chess", "COMMUNITY"),
            new SiteEntry("Wikipedia", "https://www.wikipedia.org", "📖", "Encyclopedia", "INFO"),
            new SiteEntry("Weather", "https://weather.com", "⛅", "Forecast & Radar", "LOCAL")
        ))
    );

    public static final List<CategoryEntry> EXTRA_CATEGORIES = ALL_CATEGORIES.subList(1, ALL_CATEGORIES.size());

    public static int getTotalExtraAppsCount() {
        int total = 0;
        for (CategoryEntry category : EXTRA_CATEGORIES) {
            total += category.sites.size();
        }
        return total;
    }

    private AppCatalog() {}
}
