package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
 * Draws a softly shaded tray behind the independent dock row and adds breathing room below it. A theme's
 * wallpaper_tints can tint the tray in the hue of the picture on screen ({@link #setTint}, colours from DockTint);
 * without a tint it keeps its dark grey-blue look. With the wallpaper's crossfade the tray fades to its next look over
 * the same time ({@link #fadeTint}): the look before it is kept ready as a second set of paints, and the two are drawn
 * with complementary alpha, so no frame of the fade allocates or builds a shader.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /** One look of the tray: its paints, its tint, and the height and tint its shaders were built for. */
    private static final class Tray {
        final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** The tray's base colour (DockTint), or {@link DockTint#NONE} for the dark tray. */
        int tint = DockTint.NONE;
        float shaderHeight = -1;
        int shaderTint = DockTint.NONE;

        Tray(float density) {
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(density);
        }

        void setAlpha(int alpha) {
            band.setAlpha(alpha);
            edge.setAlpha(alpha);
            shade.setAlpha(alpha);
        }
    }

    private final int dockSize;
    private final float density;
    private final RectF rect = new RectF();
    private final RectF localRect = new RectF();
    private final boolean translucent;
    /** The tray on screen; during a fade, the one fading in. */
    private Tray tray;
    /** During a fade, the look fading out; otherwise the one before, kept with its shaders for the next fade. */
    private Tray previous;
    /** Made at the first fade, then started again for each one; null until then. */
    private ValueAnimator fader;
    /** The grid the tray draws in, redrawn on each frame of a fade; null until the first one. */
    private View grid;
    private boolean fading;

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
        tray = new Tray(density);
        previous = new Tray(density);
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
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        float side = Math.max(0f, Math.min(pad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        rect.bottom += namesRoom;
        float radius = 28 * density;
        // Local coordinates keep these shaders reusable while the entire row scrolls.
        updateShaders(tray, rect.height());
        if (fading) updateShaders(previous, rect.height());
        localRect.set(0, 0, rect.width(), rect.height());
        c.save();
        c.translate(rect.left, rect.top);
        if (fading) draw(c, previous, radius);
        draw(c, tray, radius);
        c.restore();
    }

    private void draw(Canvas c, Tray look, float radius) {
        c.drawRoundRect(localRect, radius, radius, look.band);
        if (look.tint == DockTint.NONE) c.drawRoundRect(localRect, radius, radius, look.shade);
        c.drawRoundRect(localRect, radius, radius, look.edge);
    }

    /**
     * Tints the tray in {@code baseColor}, an opaque colour from {@link DockTint#of}, or gives it back its dark look for
     * {@link DockTint#NONE}, at once; a fade in progress lands first. The shaders are rebuilt once, at the next draw.
     * Returns whether the tint changed: the caller then redraws the grid (only the drawing changes, not the dock's
     * offsets, so no layout is needed).
     */
    boolean setTint(int baseColor) {
        endFade();
        if (baseColor == tray.tint) return false;
        tray.tint = baseColor;
        return true;
    }

    /**
     * Fades the tray from the look on screen to {@code baseColor} (as {@link #setTint} takes it, NONE included) over
     * {@code duration} ms on WallpaperFade's curve, redrawing {@code grid}, the RecyclerView it draws in, on each
     * frame. A fade in progress lands first; the same tint again changes nothing.
     */
    void fadeTint(int baseColor, long duration, View grid) {
        endFade();
        if (baseColor == tray.tint) return;
        // The look on screen keeps its paints and shaders and fades out; the spare set takes the new tint, and only
        // its shaders are built, once, at the next draw (none at all when it already had this tint).
        Tray spare = previous;
        previous = tray;
        tray = spare;
        tray.tint = baseColor;
        this.grid = grid;
        if (fader == null) {
            fader = ValueAnimator.ofFloat(0f, 1f);
            fader.setInterpolator(WallpaperFade::eased);
            // getAnimatedFraction is the eased fraction as a float: no boxed value per frame.
            fader.addUpdateListener(animation -> blend(animation.getAnimatedFraction()));
            fader.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fading = false;
                    tray.setAlpha(255);
                    previous.setAlpha(255);
                    DockDecoration.this.grid.invalidate();
                }
            });
        }
        fading = true;
        blend(0f);
        fader.setDuration(duration);
        fader.start();
    }

    /** Lands a fade in progress at once, the new look fully in; for onPause and onDestroy too. */
    void endFade() {
        if (fader != null && fader.isStarted()) fader.end();
    }

    private void blend(float eased) {
        previous.setAlpha(WallpaperFade.outAlpha(eased));
        tray.setAlpha(WallpaperFade.inAlpha(eased));
        grid.invalidate();
    }

    /** Builds the shaders for this height and tint; between changes of either, every frame reuses them. */
    private void updateShaders(Tray look, float height) {
        if (height == look.shaderHeight && look.tint == look.shaderTint) return;
        look.shaderHeight = height;
        look.shaderTint = look.tint;
        if (look.tint != DockTint.NONE) {
            // A soft tray in the picture's own hue, the same for the cinematic shelf, whose artwork and glow it lets
            // through more than its translucent dark band does. No black shade: the band's bottom is already deeper.
            look.band.setShader(new LinearGradient(0, 0, 0, height,
                    DockTint.bandTop(look.tint), DockTint.bandBottom(look.tint), Shader.TileMode.CLAMP));
            look.edge.setShader(new LinearGradient(0, 0, 0, height,
                    DockTint.edgeTop(look.tint), DockTint.edgeBottom(look.tint), Shader.TileMode.CLAMP));
            return;
        }
        look.band.setShader(new LinearGradient(0, 0, 0, height,
                translucent ? 0xA8252B35 : 0xE6252B35,
                translucent ? 0xB813171F : 0xF013171F, Shader.TileMode.CLAMP));
        look.edge.setShader(new LinearGradient(0, 0, 0, height,
                0x38E2EAF6, 0x12D2DEEE, Shader.TileMode.CLAMP));
        look.shade.setShader(new LinearGradient(0, height * 0.65f, 0, height,
                0x00000000, 0x28000000, Shader.TileMode.CLAMP));
    }
}
