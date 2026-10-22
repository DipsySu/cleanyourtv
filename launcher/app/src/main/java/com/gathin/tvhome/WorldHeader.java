package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * Compact headers for the Animal Crossing and Zelda theme packs. They deliberately restyle the common header views
 * instead of replacing either locale's layout: the Hebrew layout retains its natural order and the six-column dock
 * keeps all of its vertical room. {@link #applyHeader(Activity, String)} is an onCreate operation; {@link
 * #applyPills(Activity, String)} is safe after each network-icon update.
 */
final class WorldHeader {
    private static final int LEAF = 0xFF39764A;
    private static final int LEAF_DARK = 0xFF1C4B31;
    private static final int CREAM = 0xFFFFF6D7;
    private static final int MINT = 0xFFD9F0D5;
    private static final int JADE = 0xFF1D7869;
    private static final int JADE_DARK = 0xFF102F31;
    private static final int GOLD = 0xFFD9B968;
    private static final int SLATE = 0xFF17282D;

    private WorldHeader() {}

    static boolean matches(String style) {
        return HeaderStyle.ANIMAL_CROSSING.equals(style) || HeaderStyle.ZELDA.equals(style);
    }

    static void applyHeader(Activity activity, String style) {
        if (!matches(style)) return;
        boolean animal = HeaderStyle.ANIMAL_CROSSING.equals(style);
        Resources res = activity.getResources();
        float dp = res.getDisplayMetrics().density;

        TextView greeting = activity.findViewById(R.id.greeting);
        greeting.setAllCaps(false);
        greeting.setTypeface(animal ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL));
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, animal ? 13.5f : 13f);
        greeting.setLetterSpacing(animal ? .025f : .09f);
        greeting.setTextColor(animal ? CREAM : GOLD);
        greeting.setShadowLayer(animal ? 0 : 2 * dp, 0, dp, 0x99000000);
        greeting.setCompoundDrawablePadding(Math.round(7 * dp));
        Drawable greetingMark = new MarkDrawable(animal ? MarkDrawable.LEAF : MarkDrawable.TRIFORCE,
                animal ? LEAF : GOLD);
        greetingMark.setBounds(0, 0, Math.round((animal ? 18 : 15) * dp), Math.round((animal ? 18 : 15) * dp));
        greeting.setCompoundDrawablesRelative(greetingMark, null, null, null);

        OutlineTextClock clock = activity.findViewById(R.id.clock);
        clock.setOutline(0, 0, Color.TRANSPARENT);
        clock.setTypeface(animal ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, animal ? 50 : 48);
        clock.setLetterSpacing(animal ? -.025f : .015f);
        clock.setTextColor(animal ? LEAF_DARK : CREAM);
        clock.setShadowLayer(animal ? 0 : 2 * dp, 0, dp, 0x99000000);
        clock.setBackground(panel(animal ? CREAM : SLATE, animal ? LEAF : GOLD, animal ? 16 * dp : 3 * dp,
                animal ? 1 * dp : 1.5f * dp));
        clock.setPaddingRelative(Math.round((animal ? 11 : 10) * dp), 0, Math.round((animal ? 11 : 10) * dp), 0);

        OutlineTextClock ampm = activity.findViewById(R.id.ampm);
        ampm.setOutline(0, 0, Color.TRANSPARENT);
        ampm.setTypeface(animal ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ampm.setLetterSpacing(animal ? .03f : .07f);
        ampm.setTextColor(animal ? CREAM : GOLD);
        ampm.setShadowLayer(2 * dp, 0, dp, 0x99000000);

        View divider = activity.findViewById(R.id.clockDivider);
        divider.setVisibility(View.VISIBLE);
        divider.setBackgroundColor(animal ? 0x6639764A : GOLD);
        TextView date = activity.findViewById(R.id.date);
        android.view.ViewGroup.LayoutParams dateParams = date.getLayoutParams();
        dateParams.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        date.setLayoutParams(dateParams);
        date.setTypeface(animal ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, animal ? 13.5f : 13);
        date.setLetterSpacing(animal ? .025f : .06f);
        date.setTextColor(animal ? LEAF_DARK : CREAM);
        date.setBackground(panel(animal ? MINT : JADE_DARK, animal ? LEAF : GOLD, animal ? 14 * dp : 2 * dp,
                animal ? 1 * dp : 1 * dp));
        date.setPaddingRelative(Math.round(9 * dp), Math.round(2 * dp), Math.round(9 * dp), Math.round(2 * dp));
    }

    static void applyPills(Activity activity, String style) {
        if (!matches(style)) return;
        boolean animal = HeaderStyle.ANIMAL_CROSSING.equals(style);
        stylePill((TextView) activity.findViewById(R.id.btnInputs), animal,
                animal ? MarkDrawable.LEAF : MarkDrawable.RUPEE, null);
        TextView wifi = activity.findViewById(R.id.btnWifi);
        // MainActivity sets the current Wi-Fi/Ethernet glyph immediately before this method. Preserve that signal.
        Drawable network = wifi.getCompoundDrawablesRelative()[0];
        // onCreate and a network change can both restyle the pill; unwrap our previous badge first.
        while (network instanceof LayerDrawable) network = ((LayerDrawable) network).getDrawable(1);
        stylePill(wifi, animal, animal ? MarkDrawable.LEAF : MarkDrawable.RUPEE, network);
        stylePill((TextView) activity.findViewById(R.id.btnSettings), animal,
                animal ? MarkDrawable.LEAF : MarkDrawable.TRIFORCE, null);
        stylePill((TextView) activity.findViewById(R.id.btnReorder), animal,
                animal ? MarkDrawable.LEAF : MarkDrawable.RUPEE, null);
        stylePill((TextView) activity.findViewById(R.id.btnLibraryTop), animal,
                animal ? MarkDrawable.LEAF : MarkDrawable.TRIFORCE, null);
    }

    private static void stylePill(TextView pill, boolean animal, int mark, Drawable network) {
        float dp = pill.getResources().getDisplayMetrics().density;
        int face = animal ? CREAM : SLATE;
        int text = animal ? LEAF_DARK : CREAM;
        int accent = animal ? LEAF : GOLD;
        pill.setBackground(selector(face, animal ? MINT : JADE_DARK, accent, animal ? 18 * dp : 3 * dp,
                animal ? 1 * dp : 1.5f * dp));
        pill.setTextColor(text);
        pill.setTypeface(animal ? android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                : android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        pill.setCompoundDrawableTintList(null);
        Drawable emblem = emblem(pill, mark, accent, network);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(emblem, null, null, null);
        if (pill.getText().length() == 0) {
            int side = Math.max(0, (pill.getLayoutParams().width - emblem.getIntrinsicWidth()) / 2);
            pill.setPaddingRelative(side, 0, side, 0);
        } else {
            pill.setPaddingRelative(Math.round(8 * dp), 0, Math.round(13 * dp), 0);
            pill.setCompoundDrawablePadding(Math.round(7 * dp));
        }
    }

    private static Drawable emblem(TextView owner, int mark, int accent, Drawable network) {
        float dp = owner.getResources().getDisplayMetrics().density;
        int size = Math.round(23 * dp);
        Drawable disc = panel(network == null ? 0x00000000 : accent, accent, 12 * dp, 1.2f * dp);
        Drawable symbol = network == null ? new MarkDrawable(mark, accent) : network.mutate();
        if (network != null) symbol.clearColorFilter();
        LayerDrawable result = new LayerDrawable(new Drawable[]{disc, symbol});
        result.setLayerSize(0, size, size);
        result.setLayerGravity(1, Gravity.CENTER);
        int symbolSize = Math.round((network == null ? 13 : 14) * dp);
        result.setLayerSize(1, symbolSize, symbolSize);
        return result;
    }

    private static Drawable selector(int normal, int focused, int stroke, float radius, float width) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, panel(focused, stroke, radius, width));
        states.addState(new int[]{android.R.attr.state_focused}, panel(focused, stroke, radius, width));
        states.addState(new int[0], panel(normal, stroke, radius, width));
        return states;
    }

    private static GradientDrawable panel(int color, int stroke, float radius, float width) {
        GradientDrawable panel = new GradientDrawable();
        panel.setShape(GradientDrawable.RECTANGLE);
        panel.setColor(color);
        panel.setCornerRadius(radius);
        panel.setStroke(Math.max(1, Math.round(width)), stroke);
        return panel;
    }

    /** Small drawables keep the headers self-contained and avoid visual assets that compete with the dock artwork. */
    private static final class MarkDrawable extends Drawable {
        static final int LEAF = 1, TRIFORCE = 2, RUPEE = 3;
        private final int kind, color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MarkDrawable(int kind, int color) { this.kind = kind; this.color = color; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            path.reset();
            if (kind == LEAF) {
                path.moveTo(x + .12f * w, y + .78f * h);
                path.cubicTo(x + .08f * w, y + .23f * h, x + .55f * w, y + .04f * h, x + .9f * w, y + .1f * h);
                path.cubicTo(x + .93f * w, y + .57f * h, x + .63f * w, y + .91f * h, x + .12f * w, y + .78f * h);
                canvas.drawPath(path, paint);
                paint.setColor(CREAM);
                paint.setStrokeWidth(Math.max(1, .08f * w));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(x + .2f * w, y + .76f * h, x + .76f * w, y + .25f * h, paint);
            } else if (kind == TRIFORCE) {
                triangle(canvas, x + .5f * w, y + .06f * h, x + .29f * w, y + .46f * h, x + .71f * w, y + .46f * h);
                triangle(canvas, x + .27f * w, y + .5f * h, x + .06f * w, y + .9f * h, x + .48f * w, y + .9f * h);
                triangle(canvas, x + .73f * w, y + .5f * h, x + .52f * w, y + .9f * h, x + .94f * w, y + .9f * h);
            } else {
                path.moveTo(x + .5f * w, y + .05f * h);
                path.lineTo(x + .86f * w, y + .5f * h);
                path.lineTo(x + .5f * w, y + .95f * h);
                path.lineTo(x + .14f * w, y + .5f * h);
                path.close();
                canvas.drawPath(path, paint);
            }
        }

        private void triangle(Canvas canvas, float ax, float ay, float bx, float by, float cx, float cy) {
            path.reset(); path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.close(); canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 23; }
        @Override public int getIntrinsicHeight() { return 23; }
    }
}
