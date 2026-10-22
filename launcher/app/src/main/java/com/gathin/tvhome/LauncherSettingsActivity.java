package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[2];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;
    private boolean odyssey;
    private Map<String, String> wallpaperNames;
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    /** Incremented when a picker closes, so stale thumbnail work cannot update its recycled rows. */
    private int pickerGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        odyssey = HeaderStyle.ODYSSEY.equals(config.headerStyle);
        wallpaperNames = config.wallpaperNames;
        applyTheme();
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns6};
        int[] values = {4, 6};
        for (int i = 0; i < ids.length; i++) {
            final int value = values[i];
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[columns == 6 ? 1 : 0].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        List<String> names = new ArrayList<>();
        for (File image : images) names.add(image.getName());
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        RecyclerView list = new RecyclerView(this);
        int inset = dp(12);
        list.setPadding(inset, 0, inset, 0);
        list.setClipToPadding(false);
        list.setLayoutManager(new LinearLayoutManager(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        int generation = ++pickerGeneration;
        WallpaperChoicesAdapter adapter = new WallpaperChoicesAdapter(images, names, current, dialog, generation);
        list.setAdapter(adapter);
        dialog.setOnShowListener(ignored -> {
            int selected = WallpaperPickerChoices.selectedIndex(names, current);
            int target = selected < 0 ? 0 : selected;
            list.scrollToPosition(target);
            list.post(() -> {
                RecyclerView.ViewHolder deferred = list.findViewHolderForAdapterPosition(target);
                if (deferred != null) deferred.itemView.requestFocus();
            });
            styleOdysseyDialog(dialog);
        });
        dialog.setOnDismissListener(ignored -> {
            if (pickerGeneration == generation) pickerGeneration++;
            adapter.clearVisibleThumbnails(list);
        });
        dialog.show();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == (i == 0 ? 4 : 6));
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        animationsChoice.setSelected(animations);
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setSelected(advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                WallpaperPickerChoices.NONE.equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? WallpaperPickerChoices.displayName(wallpaperCurrent, wallpaperNames)
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        thumbnailExecutor.shutdownNow();
        super.onDestroy();
    }

    private void applyTheme() {
        if (!odyssey) return;
        findViewById(R.id.settingsRoot).setBackgroundResource(R.drawable.settings_odyssey_background);
        int cream = 0xFFFFF2D1;
        int gold = 0xFFE1AB35;
        ((TextView) findViewById(R.id.settingsTitle)).setTextColor(cream);
        ((TextView) findViewById(R.id.columnsLabel)).setTextColor(gold);
        ((TextView) findViewById(R.id.rowsLabel)).setTextColor(gold);
        ((TextView) findViewById(R.id.advancedHint)).setTextColor(0xFFD8C7B0);
        ((TextView) findViewById(R.id.followThemeHint)).setTextColor(0xFFD8C7B0);
        ((TextView) findViewById(R.id.homeSettingsHint)).setTextColor(0xFFD8C7B0);
        int[] choices = {R.id.columns4, R.id.columns6, R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3,
                R.id.wallpaperChoice, R.id.animationsChoice, R.id.advancedChoice, R.id.followThemeChoice,
                R.id.defaultHomeChoice, R.id.tvSettingsChoice};
        for (int id : choices) {
            TextView choice = findViewById(id);
            choice.setBackgroundResource(R.drawable.settings_odyssey_choice_bg);
            choice.setTextColor(odysseyTextColor(cream));
        }
    }

    private ColorStateList odysseyTextColor(int normal) {
        return new ColorStateList(new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0xFF34204A, normal});
    }

    private void styleOdysseyDialog(AlertDialog dialog) {
        if (!odyssey) return;
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(R.drawable.settings_odyssey_dialog_bg);
        SpannableString title = new SpannableString(getString(R.string.wallpaper_label));
        title.setSpan(new ForegroundColorSpan(0xFFFFF2D1), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dialog.setTitle(title);
        TextView cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) cancel.setTextColor(0xFFE1AB35);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class WallpaperChoicesAdapter extends RecyclerView.Adapter<WallpaperChoicesAdapter.Holder> {
        private final List<File> images;
        private final List<String> filenames;
        private final String selected;
        private final AlertDialog dialog;
        private final int generation;

        WallpaperChoicesAdapter(List<File> images, List<String> filenames, String selected, AlertDialog dialog,
                                int generation) {
            this.images = images;
            this.filenames = filenames;
            this.selected = selected;
            this.dialog = dialog;
            this.generation = generation;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(LauncherSettingsActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            row.setFocusable(true);
            row.setClickable(true);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(84));
            params.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(params);
            ImageView thumbnail = new ImageView(LauncherSettingsActivity.this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(0xFF34204A);
            thumbnail.setContentDescription(null);
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(thumbnail, new LinearLayout.LayoutParams(dp(112), dp(64)));
            LinearLayout words = new LinearLayout(LauncherSettingsActivity.this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(0, -1, 1f);
            wordParams.setMargins(dp(14), 0, dp(8), 0);
            row.addView(words, wordParams);
            TextView label = new TextView(LauncherSettingsActivity.this);
            label.setTextSize(18);
            label.setTextColor(odyssey ? 0xFFFFF2D1 : 0xFFFFFFFF);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(label, new LinearLayout.LayoutParams(-1, -2));
            TextView status = new TextView(LauncherSettingsActivity.this);
            status.setTextSize(13);
            status.setTextColor(odyssey ? 0xFFD8C7B0 : 0xFFACB8C1);
            status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(status, new LinearLayout.LayoutParams(-1, -2));
            RadioButton checked = new RadioButton(LauncherSettingsActivity.this);
            checked.setClickable(false);
            checked.setFocusable(false);
            checked.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(checked, new LinearLayout.LayoutParams(-2, -2));
            return new Holder(row, thumbnail, label, status, checked);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            String value = position == 0 ? null : position == 1 ? WallpaperPickerChoices.NONE
                    : filenames.get(position - 2);
            boolean current = value == null ? selected == null : value.equals(selected);
            String label = value == null ? getString(R.string.wallpaper_theme_rotation)
                    : WallpaperPickerChoices.NONE.equals(value) ? getString(R.string.wallpaper_none)
                    : WallpaperPickerChoices.displayName(value, wallpaperNames);
            String status = value == null ? getString(R.string.wallpaper_theme_rotation_hint)
                    : WallpaperPickerChoices.NONE.equals(value) ? getString(R.string.wallpaper_off_hint)
                    : getString(R.string.wallpaper_fixed_hint);
            holder.label.setText(label);
            holder.status.setText(status);
            holder.checked.setChecked(current);
            holder.itemView.setSelected(current);
            holder.itemView.setBackgroundResource(odyssey ? R.drawable.settings_odyssey_choice_bg
                    : R.drawable.settings_choice_bg);
            holder.itemView.setContentDescription(getString(R.string.wallpaper_option_description, label,
                    current ? getString(R.string.wallpaper_current) : getString(R.string.wallpaper_not_current)));
            holder.thumbnail.setImageBitmap(null);
            holder.thumbnail.setTag(null);
            if (position < 2) {
                holder.thumbnail.setVisibility(View.GONE);
            } else {
                holder.thumbnail.setVisibility(View.VISIBLE);
                File image = images.get(position - 2);
                holder.thumbnail.setTag(image.getAbsolutePath());
                loadThumbnail(holder.thumbnail, image, generation, dialog);
            }
            holder.itemView.setOnFocusChangeListener((view, focused) -> applyPickerFocus(holder, focused, current));
            applyPickerFocus(holder, holder.itemView.hasFocus(), current);
            holder.itemView.setOnClickListener(v -> {
                if (value == null) choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                else choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, value).apply();
                setResult(RESULT_OK);
                dialog.dismiss();
                Toast.makeText(LauncherSettingsActivity.this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                refresh();
            });
        }

        @Override public int getItemCount() { return filenames.size() + 2; }

        @Override public void onViewRecycled(@NonNull Holder holder) {
            holder.thumbnail.setTag(null);
            holder.thumbnail.setImageBitmap(null);
            super.onViewRecycled(holder);
        }

        void clearVisibleThumbnails(RecyclerView list) {
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                RecyclerView.ViewHolder viewHolder = list.getChildViewHolder(child);
                if (viewHolder instanceof Holder) {
                    Holder holder = (Holder) viewHolder;
                    holder.thumbnail.setTag(null);
                    holder.thumbnail.setImageBitmap(null);
                }
            }
        }

        private void applyPickerFocus(Holder holder, boolean focused, boolean selected) {
            if (!odyssey) return;
            holder.label.setTextColor(focused ? 0xFF34204A : 0xFFFFF2D1);
            holder.status.setTextColor(focused ? 0xFF34204A : 0xFFD8C7B0);
            int color = focused ? 0xFF34204A : selected ? 0xFFE1AB35 : 0xFFD8C7B0;
            holder.checked.setButtonTintList(ColorStateList.valueOf(color));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumbnail;
            final TextView label;
            final TextView status;
            final RadioButton checked;

            Holder(View view, ImageView thumbnail, TextView label, TextView status, RadioButton checked) {
                super(view);
                this.thumbnail = thumbnail;
                this.label = label;
                this.status = status;
                this.checked = checked;
            }
        }
    }

    private void loadThumbnail(ImageView target, File image, int generation, AlertDialog dialog) {
        final String expected = image.getAbsolutePath();
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = decodeThumbnail(image, dp(112), dp(64));
            target.post(() -> {
                boolean usable = !isFinishing() && !isDestroyed() && dialog.isShowing()
                        && generation == pickerGeneration && expected.equals(target.getTag());
                if (usable) target.setImageBitmap(bitmap);
                else if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            });
        });
    }

    private static Bitmap decodeThumbnail(File image, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > targetWidth || bounds.outHeight / sample > targetHeight) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(image.getAbsolutePath(), decode);
    }
}
