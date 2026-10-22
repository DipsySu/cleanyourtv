package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The "game HUD" header, header_style odyssey: a Power Moon before the greeting (with the wallpaper's kingdom, see
 * HeaderStyle), a coin before a heavy rounded clock with a dark outline and a hard shadow, the date in a purple pill,
 * and cream pills whose icons sit in coloured badges and that turn gold when focused. The coin is the gold vector one,
 * or the kingdom's purple coin wallpaper_coins gives the picture on screen. It restyles the views activity_main.xml
 * inflated, the default layout or the Hebrew one alike, so neither layout needs an Odyssey copy.
 *
 * Everything here runs once from onCreate, when the network pill's icon changes or when the wallpaper changes (with
 * the wallpaper's crossfade, once the new picture is half in: WallpaperFade.halfway); the icons are vector and shape
 * drawables, the kingdoms' coins small bitmaps that MainActivity keeps once decoded, and the outline is drawn by
 * OutlineTextClock without allocating per frame.
 */
final class OdysseyHeader {
    /** Outlines, borders and hard edges: the deep purple ink of the mockup. */
    static final int INK = 0xFF1B1035;
    private static final int CREAM_GOLD = 0xFFFFE7A6;
    /** Badge colours: Inputs red, network blue, Settings gold, and a slate for the round extra buttons. */
    private static final int RED = 0xFFE3312D, BLUE = 0xFF2E7BE6, GOLD = 0xFFFFC928, SLATE = 0xFF6B6F86;
    /**
     * Settings' badge on a focused or pressed pill: GOLD is the end colour of the gold face (odyssey_pill_bg), so the
     * disc would vanish into it and leave only its ring; a deeper amber keeps it a disc.
     */
    private static final int GOLD_ON_GOLD = 0xFFE89A00;
    private static final float CLOCK_SP = 58;
    /** The coin before the clock: ic_odyssey_coin's size, which a kingdom's coin takes too. */
    private static final float COIN_DP = 46;
    private static final float BADGE_DP = 29, GLYPH_DP = 17, EYES_DP = 20;
    /** The hard edge under a pill's face, inside its 42dp (odyssey_pill_bg): the content centres on the face above. */
    private static final float EDGE_DP = 3.5f;

    private OdysseyHeader() {}

    /**
     * Restyles the greeting, clock, AM/PM and date, with {@code coin} (from {@link #coin}) before the clock. Call it
     * with the greeting's real text already set: the header block is measured before and after, and grows upwards by
     * the difference, so the pills (aligned to its bottom) and the grid under it stay exactly where the default header
     * has them.
     */
    static void applyHeader(Activity activity, Drawable coin) {
        Resources res = activity.getResources();
        float dp = res.getDisplayMetrics().density;
        View block = activity.findViewById(R.id.leftBlock);
        int before = naturalHeight(block);

        TextView greeting = activity.findViewById(R.id.greeting);
        greeting.setAllCaps(false);   // a kingdom keeps the case the theme wrote it in
        greeting.setTypeface(Typeface.DEFAULT_BOLD);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        greeting.setLetterSpacing(0.04f);
        greeting.setTextColor(CREAM_GOLD);
        greeting.setShadowLayer(3 * dp, 0, dp, 0x99000000);
        greeting.setCompoundDrawablePadding(Math.round(6 * dp));
        if (res.getBoolean(R.bool.rtl_menu_bar)) {
            // The Hebrew layout centres the greeting over the clock in a line as wide as the clock row, which would
            // leave the moon at its left end, far from the text. Wrapped and centred, the line keeps the moon beside
            // the text, on its right, where a right-to-left reader starts (the launcher lays out left to right).
            greeting.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_odyssey_moon, 0);
            LinearLayout.LayoutParams greetingParams = (LinearLayout.LayoutParams) greeting.getLayoutParams();
            greetingParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            greetingParams.gravity = Gravity.CENTER_HORIZONTAL;
            greeting.setLayoutParams(greetingParams);
        } else {
            greeting.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_odyssey_moon, 0, 0, 0);
        }

        // The digits: Fredoka at weight 700 and width 120, a white fill in a 3.5dp ink outline, the outlined digits again
        // 3dp lower as a hard shadow, and the coin as the start drawable. The wide instance and the letter spacing keep
        // the digits 7 to 10 px apart, so each keeps its own outline. Formats (12 or 24 hours) stay those of the layout.
        OutlineTextClock clock = activity.findViewById(R.id.clock);
        clock.setTypeface(res.getFont(R.font.fredoka_bold));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, CLOCK_SP);
        clock.setLetterSpacing(0.03f);
        clock.setTextColor(Color.WHITE);
        clock.setOutline(3.5f * dp, 3 * dp, INK);
        clock.setCompoundDrawablesRelative(coin, null, null, null);
        clock.setCompoundDrawablePadding(Math.round(8 * dp));
        // Room after the last digit for its outline; the descent below the digits already holds outline and shadow.
        clock.setPaddingRelative(0, 0, Math.round(2 * dp), 0);
        Paint.FontMetricsInt digits = clock.getPaint().getFontMetricsInt();

        // AM/PM (下午): bold white with a thinner outline, on the clock's baseline instead of the row's bottom.
        OutlineTextClock ampm = activity.findViewById(R.id.ampm);
        ampm.setTypeface(Typeface.DEFAULT_BOLD);
        ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ampm.setLetterSpacing(0.02f);
        ampm.setTextColor(Color.WHITE);
        ampm.setOutline(2.5f * dp, 0, INK);
        int bleed = Math.round(2 * dp);   // the outline reaches past the first and last glyph
        ampm.setPaddingRelative(bleed, 0, bleed, 0);
        LinearLayout.LayoutParams ampmParams = (LinearLayout.LayoutParams) ampm.getLayoutParams();
        ampmParams.gravity = Gravity.TOP;   // with the clock: the row's baselineAligned lines their baselines up
        ampmParams.bottomMargin = 0;
        ampmParams.setMarginStart(ampmParams.getMarginStart() - bleed);
        ampm.setLayoutParams(ampmParams);   // resolves the start margin again

        // The thin divider gives way to a gap the same in both layouts (it stays as a spacer, unseen).
        View divider = activity.findViewById(R.id.clockDivider);
        divider.setVisibility(View.INVISIBLE);
        ViewGroup.MarginLayoutParams dividerParams = (ViewGroup.MarginLayoutParams) divider.getLayoutParams();
        dividerParams.setMarginStart(Math.round(5 * dp));
        dividerParams.setMarginEnd(Math.round(5 * dp));
        divider.setLayoutParams(dividerParams);

        // The date in a purple pill whose bottom sits on the clock's baseline.
        TextView date = activity.findViewById(R.id.date);
        date.setBackgroundResource(R.drawable.odyssey_date_bg);
        date.setTypeface(Typeface.DEFAULT_BOLD);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        date.setLetterSpacing(0.04f);
        date.setTextColor(Color.WHITE);
        date.setPadding(Math.round(12 * dp), Math.round(5 * dp), Math.round(12 * dp), Math.round(6 * dp));
        ViewGroup.LayoutParams dateParams = date.getLayoutParams();
        dateParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        date.setLayoutParams(dateParams);
        View dates = (View) date.getParent();
        ViewGroup.MarginLayoutParams datesParams = (ViewGroup.MarginLayoutParams) dates.getLayoutParams();
        datesParams.bottomMargin = digits.descent;
        dates.setLayoutParams(datesParams);

        // Grow upwards: the pills and the grid keep the default header's positions.
        ViewGroup.MarginLayoutParams blockParams = (ViewGroup.MarginLayoutParams) block.getLayoutParams();
        blockParams.topMargin -= naturalHeight(block) - before;
        block.setLayoutParams(blockParams);
    }

    /**
     * The coin before the clock for {@code name}, a value {@link HeaderStyle#coin} accepted: that coin_* drawable,
     * {@link #COIN_DP} square; or the gold vector coin for "" or a name the APK does not have. A coin is a 128 px WebP
     * from drawable-nodpi, drawn filtered at 46dp (92 px on a 320 dpi TV). It is decoded here, so MainActivity keeps
     * each coin it asked for and never calls this per frame.
     */
    static Drawable coin(Context context, String name) {
        Resources res = context.getResources();
        int id = name.isEmpty() ? 0 : res.getIdentifier(name, "drawable", context.getPackageName());
        Drawable coin = context.getDrawable(id != 0 ? id : R.drawable.ic_odyssey_coin);
        if (coin instanceof BitmapDrawable) ((BitmapDrawable) coin).setFilterBitmap(true);
        int size = Math.round(COIN_DP * res.getDisplayMetrics().density);
        coin.setBounds(0, 0, size, size);
        return coin;
    }

    /**
     * Puts {@code coin} (from {@link #coin}) before the clock after a change of wallpaper, halfway through its fade or at
     * once with the cut; the same one is left. A hard switch, like the kingdom's name: no alpha animates per frame.
     */
    static void setCoin(Activity activity, Drawable coin) {
        TextView clock = activity.findViewById(R.id.clock);
        if (clock.getCompoundDrawablesRelative()[0] != coin) clock.setCompoundDrawablesRelative(coin, null, null, null);
    }

    /**
     * Cream pills with a hard ink edge, each icon in a coloured badge: Cappy-style eyes on red for Inputs, the network
     * glyph on blue, a dark gear on gold for Settings, and the white glyph on slate for Reorder and the library. Call it
     * after makeIconOnly, so a round pill centres its badge; a festival's icons then replace the badges.
     */
    static void applyPills(Activity activity) {
        ColorStateList gold = new ColorStateList(
                new int[][]{{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {}},
                new int[]{GOLD_ON_GOLD, GOLD_ON_GOLD, GOLD});
        pill(activity.findViewById(R.id.btnInputs),
                badge(activity, R.drawable.ic_odyssey_eyes, ColorStateList.valueOf(RED), 0, EYES_DP));
        pill(activity.findViewById(R.id.btnWifi), networkBadge(activity, R.drawable.ic_wifi));
        pill(activity.findViewById(R.id.btnSettings), badge(activity, R.drawable.ic_settings, gold, INK, GLYPH_DP));
        pill(activity.findViewById(R.id.btnReorder),
                badge(activity, R.drawable.ic_swap_vert, ColorStateList.valueOf(SLATE), Color.WHITE, GLYPH_DP));
        pill(activity.findViewById(R.id.btnLibraryTop),
                badge(activity, R.drawable.ic_apps, ColorStateList.valueOf(SLATE), Color.WHITE, GLYPH_DP));
    }

    /** The network pill's badge for {@code glyph} (ic_wifi or ic_ethernet, as updateWifiLabel picks): white on blue. */
    static Drawable networkBadge(Context context, int glyph) {
        return badge(context, glyph, ColorStateList.valueOf(BLUE), Color.WHITE, GLYPH_DP);
    }

    private static void pill(TextView pill, Drawable badge) {
        Resources res = pill.getResources();
        float dp = res.getDisplayMetrics().density;
        pill.setBackgroundResource(R.drawable.odyssey_pill_bg);
        pill.setTextColor(pill.getContext().getColorStateList(R.color.odyssey_pill_text));
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        // The style's drawableTint would colour the whole badge; with a null tint list the badge keeps its colours
        // (the glyph's comes from a colour filter, which overrides any tint).
        pill.setCompoundDrawableTintList(null);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(badge, null, null, null);
        int bottom = Math.round(EDGE_DP * dp);
        if (TextUtils.isEmpty(pill.getText())) {
            // Round (TopButtonIcon or makeIconOnly, 42dp wide): the badge centred.
            int side = Math.max(0, (pill.getLayoutParams().width - badge.getIntrinsicWidth()) / 2);
            pill.setPaddingRelative(side, 0, side, bottom);
        } else {
            pill.setPaddingRelative(Math.round(6 * dp), 0, Math.round(17 * dp), bottom);
            pill.setCompoundDrawablePadding(Math.round(8 * dp));
        }
    }

    /**
     * A {@link #BADGE_DP} circle in {@code fill} with a 1.5dp ink border, and {@code glyph} centred in it,
     * {@code glyphDp} wide, in {@code glyphColor} (0 keeps the glyph's own colours). The pill passes its focused and
     * pressed states on to its compound drawables, so a stateful {@code fill} follows them.
     */
    private static Drawable badge(Context context, int glyph, ColorStateList fill, int glyphColor, float glyphDp) {
        float dp = context.getResources().getDisplayMetrics().density;
        int size = Math.round(BADGE_DP * dp);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(fill);
        disc.setStroke(Math.round(1.5f * dp), INK);
        disc.setSize(size, size);
        LayerDrawable badge = new LayerDrawable(new Drawable[]{disc, context.getDrawable(glyph)});
        // Mutated before the colour filter goes on: a LayerDrawable's first mutate replaces its layers with copies
        // made from their constant state, which has no colour filter, and TextView mutates every drawable it tints.
        badge.mutate();
        Drawable icon = badge.getDrawable(1);
        if (glyphColor != 0) icon.setColorFilter(new PorterDuffColorFilter(glyphColor, PorterDuff.Mode.SRC_IN));
        int width = Math.round(glyphDp * dp);
        int height = Math.round(width * (float) icon.getIntrinsicHeight() / icon.getIntrinsicWidth());
        badge.setLayerGravity(1, Gravity.CENTER);
        badge.setLayerSize(1, width, height);
        return badge;
    }

    /** The block's height on its own, single lines at most the screen wide, as layout will measure it. */
    private static int naturalHeight(View block) {
        int width = block.getResources().getDisplayMetrics().widthPixels;
        block.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return block.getMeasuredHeight();
    }
}
