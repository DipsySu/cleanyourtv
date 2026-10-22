package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws a softly shaded tray behind the independent dock row and adds breathing room below it. WallpaperPlayer owns
 * timing: MainActivity calls {@link #beginTint}, then forwards its actual wallpaper alpha to {@link #setProgress}.
 * The tray remains a single gradient throughout that transition. This avoids blending two already-transparent trays,
 * which made the band visibly lose opacity at the middle of a fade.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /** The six colours which make one tray look. Keeping them together makes an in-between look pure ARGB arithmetic. */
    private static final class Colours {
        int bandTop;
        int bandBottom;
        int edgeTop;
        int edgeBottom;
        int shadeTop;
        int shadeBottom;

        void set(int tint, boolean translucent) {
            if (tint != DockTint.NONE) {
                bandTop = DockTint.bandTop(tint);
                bandBottom = DockTint.bandBottom(tint);
                edgeTop = DockTint.edgeTop(tint);
                edgeBottom = DockTint.edgeBottom(tint);
                shadeTop = 0;
                shadeBottom = 0;
            } else {
                bandTop = DockTint.darkBandTop(translucent);
                bandBottom = DockTint.darkBandBottom(translucent);
                edgeTop = DockTint.darkEdgeTop();
                edgeBottom = DockTint.darkEdgeBottom();
                shadeTop = 0;
                shadeBottom = DockTint.darkShadeBottom();
            }
        }

        void copyFrom(Colours other) {
            bandTop = other.bandTop;
            bandBottom = other.bandBottom;
            edgeTop = other.edgeTop;
            edgeBottom = other.edgeBottom;
            shadeTop = other.shadeTop;
            shadeBottom = other.shadeBottom;
        }

        void blend(Colours from, Colours to, float progress) {
            bandTop = DockTint.interpolate(from.bandTop, to.bandTop, progress);
            bandBottom = DockTint.interpolate(from.bandBottom, to.bandBottom, progress);
            edgeTop = DockTint.interpolate(from.edgeTop, to.edgeTop, progress);
            edgeBottom = DockTint.interpolate(from.edgeBottom, to.edgeBottom, progress);
            shadeTop = DockTint.interpolate(from.shadeTop, to.shadeTop, progress);
            shadeBottom = DockTint.interpolate(from.shadeBottom, to.shadeBottom, progress);
        }

        boolean sameAs(Colours other) {
            return bandTop == other.bandTop && bandBottom == other.bandBottom && edgeTop == other.edgeTop
                    && edgeBottom == other.edgeBottom && shadeTop == other.shadeTop && shadeBottom == other.shadeBottom;
        }
    }

    private final int dockSize;
    private final float density;
    private final RectF rect = new RectF();
    private final RectF localRect = new RectF();
    private final boolean translucent;
    private final Colours shown = new Colours();
    private final Colours start = new Colours();
    private final Colours target = new Colours();
    private boolean fading;
    private float shaderHeight = -1;
    private final int[] shaderColours = new int[6];
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge;
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names) {
        this.dockSize = dockSize;
        this.density = density;
        this.translucent = translucent;
        this.namesRoom = names ? 22 * density : 0;
        edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        shown.set(DockTint.NONE, translucent);
        target.copyFrom(shown);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its edges.
        float side = Math.max(0f, Math.min(pad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        rect.bottom += namesRoom;
        float radius = 28 * density;
        updateShaders(rect.height());
        localRect.set(0, 0, rect.width(), rect.height());
        c.save();
        c.translate(rect.left, rect.top);
        c.drawRoundRect(localRect, radius, radius, band);
        if (shown.shadeBottom != 0) c.drawRoundRect(localRect, radius, radius, shade);
        c.drawRoundRect(localRect, radius, radius, edge);
        c.restore();
    }

    /** Sets a tray look at once, first landing any in-flight transition. */
    boolean setTint(int baseColor) {
        endFade();
        target.set(baseColor, translucent);
        if (shown.sameAs(target)) return false;
        shown.copyFrom(target);
        return true;
    }

    /** Starts a transition from the current rendered colours to {@code baseColor}; the caller supplies progress. */
    boolean beginTint(int baseColor) {
        endFade();
        target.set(baseColor, translucent);
        if (shown.sameAs(target)) return false;
        start.copyFrom(shown);
        fading = true;
        return true;
    }

    /** Applies WallpaperPlayer's eased alpha. This makes no animations and allocates no bitmap or offscreen buffer. */
    boolean setProgress(float progress) {
        if (!fading) return false;
        float clamped = Math.max(0f, Math.min(1f, progress));
        shown.blend(start, target, clamped);
        if (clamped >= 1f) fading = false;
        return true;
    }

    /** Lands the tray on its target after wallpaper stop, cancellation, or destruction. */
    void endFade() {
        if (fading) setProgress(1f);
    }

    /** Builds only the small shader objects necessary for a changed height or colour; no bitmap/buffer is used. */
    private void updateShaders(float height) {
        if (height == shaderHeight && shaderColours[0] == shown.bandTop && shaderColours[1] == shown.bandBottom
                && shaderColours[2] == shown.edgeTop && shaderColours[3] == shown.edgeBottom
                && shaderColours[4] == shown.shadeTop && shaderColours[5] == shown.shadeBottom) return;
        shaderHeight = height;
        shaderColours[0] = shown.bandTop;
        shaderColours[1] = shown.bandBottom;
        shaderColours[2] = shown.edgeTop;
        shaderColours[3] = shown.edgeBottom;
        shaderColours[4] = shown.shadeTop;
        shaderColours[5] = shown.shadeBottom;
        band.setShader(new LinearGradient(0, 0, 0, height, shown.bandTop, shown.bandBottom, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0, 0, 0, height, shown.edgeTop, shown.edgeBottom, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, height * 0.65f, 0, height,
                shown.shadeTop, shown.shadeBottom, Shader.TileMode.CLAMP));
    }
}
