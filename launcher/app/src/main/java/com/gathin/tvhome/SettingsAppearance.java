package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.view.View;
import android.widget.TextView;

/** The small settings-only palette shared by the screen and its choice dialogs. */
final class SettingsAppearance {
    private static final int FOCUSED = android.R.attr.state_focused;
    private static final int PRESSED = android.R.attr.state_pressed;
    private static final int SELECTED = android.R.attr.state_selected;

    final int background;
    final int row;
    final int primary;
    final int secondary;
    final int accent;
    final int focusFace;
    final int focusText;
    final float radius;

    private SettingsAppearance(int background, int row, int primary, int secondary, int accent,
                               int focusFace, int focusText, float radius) {
        this.background = background;
        this.row = row;
        this.primary = primary;
        this.secondary = secondary;
        this.accent = accent;
        this.focusFace = focusFace;
        this.focusText = focusText;
        this.radius = radius;
    }

    static SettingsAppearance forHeader(String headerStyle) {
        if (HeaderStyle.ANIMAL_CROSSING.equals(headerStyle)) {
            return new SettingsAppearance(0xFF142B23, 0xFF234236, 0xFFFFF6D7, 0xFFB8CDB8,
                    0xFF4C9A63, 0xFFFFF6D7, 0xFF234236, 14f);
        }
        if (HeaderStyle.ZELDA.equals(headerStyle)) {
            return new SettingsAppearance(0xFF203128, 0xFF304238, 0xFFFFF6DD, 0xFFBDC8AC,
                    0xFFD8C18A, 0xFFD8C18A, 0xFF203128, 12f);
        }
        return null;
    }

    void applyRoot(View root) {
        root.setBackgroundColor(background);
    }

    void applyChoice(TextView choice) {
        choice.setBackground(rowBackground());
        choice.setTextColor(text(primary));
    }

    void applyDialogButton(TextView button) {
        if (button == null) return;
        button.setTextColor(buttonText());
        button.setBackground(buttonBackground());
    }

    ColorStateList text(int normal) {
        return new ColorStateList(new int[][] {new int[] {FOCUSED}, new int[] {PRESSED}, StateSet.WILD_CARD},
                new int[] {focusText, focusText, normal});
    }

    ColorStateList buttonText() {
        return new ColorStateList(new int[][] {new int[] {FOCUSED}, new int[] {PRESSED}, StateSet.WILD_CARD},
                new int[] {focusText, focusText, accent});
    }

    ColorStateList radio(boolean current, boolean focused) {
        return ColorStateList.valueOf(focused ? focusText : current ? accent : secondary);
    }

    GradientDrawable dialogBackground() {
        return shape(background, dp(1), accent, radius);
    }

    StateListDrawable rowBackground() {
        StateListDrawable out = new StateListDrawable();
        // Bright fill means focus; an outline means the saved value. Gold focus needs a dark outline.
        int selectedInk = focusFace == accent ? focusText : accent;
        out.addState(new int[] {FOCUSED, SELECTED}, shape(focusFace, dp(3), selectedInk, radius));
        out.addState(new int[] {PRESSED, SELECTED}, shape(focusFace, dp(3), selectedInk, radius));
        out.addState(new int[] {FOCUSED}, shape(focusFace, 0, Color.TRANSPARENT, radius));
        out.addState(new int[] {PRESSED}, shape(focusFace, 0, Color.TRANSPARENT, radius));
        int selectedFace = Color.rgb((Color.red(row) * 4 + Color.red(accent)) / 5,
                (Color.green(row) * 4 + Color.green(accent)) / 5,
                (Color.blue(row) * 4 + Color.blue(accent)) / 5);
        out.addState(new int[] {SELECTED}, shape(selectedFace, dp(2), accent, radius));
        out.addState(StateSet.WILD_CARD, shape(row, 0, Color.TRANSPARENT, radius));
        return out;
    }

    private StateListDrawable buttonBackground() {
        StateListDrawable out = new StateListDrawable();
        out.addState(new int[] {FOCUSED, PRESSED}, shape(focusFace, dp(2), accent, radius));
        out.addState(new int[] {FOCUSED}, shape(focusFace, dp(2), accent, radius));
        out.addState(new int[] {PRESSED}, shape(focusFace, dp(1), accent, radius));
        out.addState(StateSet.WILD_CARD, shape(Color.TRANSPARENT, 0, Color.TRANSPARENT, radius));
        return out;
    }

    private GradientDrawable shape(int fill, int strokeWidth, int stroke, float cornerRadius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(Math.round(cornerRadius)));
        if (strokeWidth > 0) shape.setStroke(strokeWidth, stroke);
        return shape;
    }

    private static int dp(int value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
}
