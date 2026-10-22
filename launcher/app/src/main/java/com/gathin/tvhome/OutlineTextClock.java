package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextClock;

/**
 * A TextClock that can draw its text with a dark outline and a hard shadow under it, like the coin counter of a
 * platform game (header_style odyssey, see OdysseyHeader). Without an outline, the default, it draws exactly as
 * TextClock does, so the default header is unchanged.
 *
 * The outline is extra passes over TextView's own Layout with the paint set to fill and stroke, drawn before
 * TextView's fill pass: the fill then covers the inner half of the stroke, and the strokes of touching digits never
 * cut into each other. The paint's style is put back before TextView draws, and TextView sets the paint's colour itself
 * at the start of its onDraw; nothing here calls setTextColor, which invalidates and would redraw from inside onDraw
 * forever. Nothing is allocated per frame.
 */
public class OutlineTextClock extends TextClock {
    /** Stroke width in px; 0 draws a plain TextClock. */
    private float outlineWidth;
    /** How far below the text its hard shadow (the outlined text again, in the outline colour) sits, in px. */
    private float shadowDrop;
    private int outlineColor;

    public OutlineTextClock(Context context) {
        super(context);
    }

    public OutlineTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Outlines the text with a {@code width} px stroke in {@code color}, half of it outside the glyphs, and drops a
     * shadow of the outlined text {@code drop} px lower (0 for none). A width of 0 turns both off.
     */
    void setOutline(float width, float drop, int color) {
        outlineWidth = width;
        shadowDrop = drop;
        outlineColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (outlineWidth > 0 && layout != null) {
            TextPaint paint = getPaint();
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(outlineWidth);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(outlineColor);
            int saved = canvas.save();
            // Where TextView draws the text: past the padding and a start drawable (the coin). The header's clocks
            // are wrap_content and top gravity, so TextView adds no vertical offset of its own.
            canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop() + shadowDrop);
            if (shadowDrop > 0) {
                layout.draw(canvas);
                canvas.translate(0, -shadowDrop);
            }
            layout.draw(canvas);
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(0);
            paint.setStrokeJoin(Paint.Join.MITER);
        }
        super.onDraw(canvas);
    }
}
