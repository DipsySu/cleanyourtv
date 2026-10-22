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
import android.text.TextUtils;
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
    private static final int IMPORT_THEME_REQUEST = 401;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[2];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView themeChoice;
    private TextView importThemeChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;
    private boolean odyssey;
    private SettingsAppearance appearance;
    private Map<String, String> wallpaperNames;
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService themeExecutor = Executors.newSingleThreadExecutor();
    /** Incremented when a picker closes, so stale thumbnail work cannot update its recycled rows. */
    private int pickerGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        odyssey = HeaderStyle.ODYSSEY.equals(config.headerStyle);
        appearance = SettingsAppearance.forHeader(config.headerStyle);
        wallpaperNames = config.wallpaperNames;
        applyTheme();
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        importThemeChoice = findViewById(R.id.importThemeChoice);
        importThemeChoice.setOnClickListener(v -> pickThemeZip());

        int[] ids = {R.id.columns4, R.id.columns6};
        int[] values = {4, 6};
        for (int i = 0; i < ids.length; i++) {
            final int value = values[i];
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                saveChoice(choices.edit().putInt("columns", value));
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
                saveChoice(choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value));
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
            saveChoice(choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects));
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (ignoredDialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
                dialog.setOnShowListener(ignored -> styleDialog(dialog, R.string.advanced_effects));
                dialog.show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            saveChoice(choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY));
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        if (getIntent().getBooleanExtra("focus_theme", false)) {
            getIntent().removeExtra("focus_theme");
            themeChoice.requestFocus();
        } else {
            columnChoices[columns == 6 ? 1 : 0].requestFocus();
        }
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = ThemeStore.activeDir(this);
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
            styleDialog(dialog, R.string.wallpaper_label);
        });
        dialog.setOnDismissListener(ignored -> {
            if (pickerGeneration == generation) pickerGeneration++;
            adapter.clearVisibleThumbnails(list);
        });
        dialog.show();
    }

    private void showThemePicker() {
        List<ThemeStore.Entry> entries = ThemeStore.list(this);
        String activeId = ThemeStore.activeId(this);
        RecyclerView list = new RecyclerView(this);
        int inset = dp(12);
        list.setPadding(inset, 0, inset, 0);
        list.setClipToPadding(false);
        list.setLayoutManager(new LinearLayoutManager(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_label)
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        ThemeChoicesAdapter adapter = new ThemeChoicesAdapter(entries, activeId, dialog);
        list.setAdapter(adapter);
        dialog.setOnShowListener(ignored -> {
            int target = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (activeId.equals(entries.get(i).id)) {
                    target = i;
                    break;
                }
            }
            list.scrollToPosition(target);
            final int position = target;
            list.post(() -> {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.requestFocus();
            });
            styleDialog(dialog, R.string.theme_label);
        });
        dialog.show();
    }

    private void pickThemeZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "application/x-zip-compressed", "application/x-zip"
        });
        try {
            startActivityForResult(intent, IMPORT_THEME_REQUEST);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.theme_chooser_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_THEME_REQUEST || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        importThemeZip(data);
    }

    private void importThemeZip(Intent data) {
        final android.net.Uri uri = data.getData();
        if (uri == null) return;
        importThemeChoice.setEnabled(false);
        importThemeChoice.setAlpha(0.5f);
        Toast.makeText(this, R.string.theme_importing, Toast.LENGTH_SHORT).show();
        themeExecutor.execute(() -> {
            ThemeStore.Entry entry = null;
            Exception failure = null;
            try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) throw new java.io.IOException("No input stream");
                entry = ThemeStore.importZip(getApplicationContext(), stream);
            } catch (Exception e) {
                failure = e;
            }
            final ThemeStore.Entry imported = entry;
            final Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                importThemeChoice.setEnabled(true);
                importThemeChoice.setAlpha(1f);
                if (error == null && imported != null) {
                    Toast.makeText(this, R.string.theme_imported, Toast.LENGTH_LONG).show();
                    refresh();
                } else {
                    Toast.makeText(this, R.string.theme_import_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void activateTheme(String id, AlertDialog dialog) {
        themeExecutor.execute(() -> {
            Exception failure = null;
            try {
                ThemeStore.activate(getApplicationContext(), id);
            } catch (Exception e) {
                failure = e;
            }
            final Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (error != null) {
                    Toast.makeText(this, R.string.theme_activation_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (dialog.isShowing()) dialog.dismiss();
                setResult(RESULT_OK);
                getIntent().putExtra("focus_theme", true);
                recreate();
            });
        });
    }

    /** Save the value and its revision together, so Home notices Back, Home and other return paths alike. */
    private void saveChoice(SharedPreferences.Editor editor) {
        editor.putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        setResult(RESULT_OK);
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        saveChoice(choices.edit().putBoolean("advanced_effects", enabled));
        refresh();
    }

    private void refresh() {
        if (themeChoice != null) {
            String activeId = ThemeStore.activeId(this);
            String activeName = activeId;
            for (ThemeStore.Entry entry : ThemeStore.list(this)) {
                if (activeId.equals(entry.id)) {
                    activeName = entry.name;
                    break;
                }
            }
            themeChoice.setText(getString(R.string.theme_state, activeName));
        }
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
        themeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void applyTheme() {
        if (!odyssey && appearance == null) return;
        int cream = odyssey ? 0xFFFFF2D1 : appearance.primary;
        int accent = odyssey ? 0xFFE1AB35 : appearance.accent;
        int secondary = odyssey ? 0xFFD8C7B0 : appearance.secondary;
        if (odyssey) findViewById(R.id.settingsRoot).setBackgroundResource(R.drawable.settings_odyssey_background);
        else appearance.applyRoot(findViewById(R.id.settingsRoot));
        ((TextView) findViewById(R.id.settingsTitle)).setTextColor(cream);
        ((TextView) findViewById(R.id.columnsLabel)).setTextColor(accent);
        ((TextView) findViewById(R.id.rowsLabel)).setTextColor(accent);
        ((TextView) findViewById(R.id.advancedHint)).setTextColor(secondary);
        ((TextView) findViewById(R.id.followThemeHint)).setTextColor(secondary);
        ((TextView) findViewById(R.id.homeSettingsHint)).setTextColor(secondary);
        int[] choices = {R.id.themeChoice, R.id.importThemeChoice, R.id.columns4, R.id.columns6, R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3,
                R.id.wallpaperChoice, R.id.animationsChoice, R.id.advancedChoice, R.id.followThemeChoice,
                R.id.defaultHomeChoice, R.id.tvSettingsChoice};
        for (int id : choices) {
            TextView choice = findViewById(id);
            if (odyssey) {
                choice.setBackgroundResource(R.drawable.settings_odyssey_choice_bg);
                choice.setTextColor(odysseyTextColor(cream));
            } else {
                appearance.applyChoice(choice);
            }
        }
    }

    private ColorStateList odysseyTextColor(int normal) {
        return new ColorStateList(new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0xFF34204A, normal});
    }

    private void styleDialog(AlertDialog dialog, int titleResource) {
        if (!odyssey && appearance == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            if (odyssey) window.setBackgroundDrawableResource(R.drawable.settings_odyssey_dialog_bg);
            else window.setBackgroundDrawable(appearance.dialogBackground());
        }
        SpannableString title = new SpannableString(getString(titleResource));
        int primary = odyssey ? 0xFFFFF2D1 : appearance.primary;
        title.setSpan(new ForegroundColorSpan(primary), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dialog.setTitle(title);
        TextView cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        TextView confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        TextView message = dialog.findViewById(android.R.id.message);
        if (odyssey) {
            if (cancel != null) cancel.setTextColor(0xFFE1AB35);
            if (confirm != null) confirm.setTextColor(0xFFE1AB35);
            if (message != null) message.setTextColor(0xFFD8C7B0);
        } else {
            appearance.applyDialogButton(cancel);
            appearance.applyDialogButton(confirm);
            if (message != null) message.setTextColor(appearance.secondary);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ThemeChoicesAdapter extends RecyclerView.Adapter<ThemeChoicesAdapter.Holder> {
        private final List<ThemeStore.Entry> entries;
        private final String activeId;
        private final AlertDialog dialog;

        ThemeChoicesAdapter(List<ThemeStore.Entry> entries, String activeId, AlertDialog dialog) {
            this.entries = entries;
            this.activeId = activeId;
            this.dialog = dialog;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(LauncherSettingsActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            row.setFocusable(true);
            row.setClickable(true);
            row.setMinimumHeight(dp(66));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(params);
            LinearLayout words = new LinearLayout(LauncherSettingsActivity.this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView label = new TextView(LauncherSettingsActivity.this);
            label.setTextSize(18);
            label.setTextColor(odyssey ? 0xFFFFF2D1 : appearance != null ? appearance.primary : 0xFFFFFFFF);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(label, new LinearLayout.LayoutParams(-1, -2));
            TextView status = new TextView(LauncherSettingsActivity.this);
            status.setTextSize(13);
            status.setTextColor(odyssey ? 0xFFD8C7B0 : appearance != null ? appearance.secondary : 0xFFACB8C1);
            status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(status, new LinearLayout.LayoutParams(-1, -2));
            RadioButton checked = new RadioButton(LauncherSettingsActivity.this);
            checked.setClickable(false);
            checked.setFocusable(false);
            checked.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(checked, new LinearLayout.LayoutParams(-2, -2));
            return new Holder(row, label, status, checked);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            ThemeStore.Entry entry = entries.get(position);
            boolean current = entry.id.equals(activeId);
            String name = entry.name;
            holder.label.setText(name);
            holder.status.setText(current ? R.string.theme_current : R.string.theme_not_current);
            holder.checked.setChecked(current);
            holder.itemView.setSelected(current);
            if (odyssey) holder.itemView.setBackgroundResource(R.drawable.settings_odyssey_choice_bg);
            else if (appearance != null) holder.itemView.setBackground(appearance.rowBackground());
            else holder.itemView.setBackgroundResource(R.drawable.settings_choice_bg);
            holder.itemView.setContentDescription(getString(R.string.theme_option_description, name,
                    current ? getString(R.string.theme_current) : getString(R.string.theme_not_current)));
            holder.itemView.setOnFocusChangeListener((view, focused) -> applyFocus(holder, focused, current));
            applyFocus(holder, holder.itemView.hasFocus(), current);
            holder.itemView.setOnClickListener(v -> {
                if (current) {
                    dialog.dismiss();
                    return;
                }
                activateTheme(entry.id, dialog);
            });
        }

        @Override public int getItemCount() { return entries.size(); }

        private void applyFocus(Holder holder, boolean focused, boolean current) {
            if (odyssey) {
                holder.label.setTextColor(focused ? 0xFF34204A : 0xFFFFF2D1);
                holder.status.setTextColor(focused ? 0xFF34204A : 0xFFD8C7B0);
                holder.checked.setButtonTintList(ColorStateList.valueOf(focused ? 0xFF34204A
                        : current ? 0xFFE1AB35 : 0xFFD8C7B0));
            } else if (appearance != null) {
                holder.label.setTextColor(focused ? appearance.focusText : appearance.primary);
                holder.status.setTextColor(focused ? appearance.focusText : appearance.secondary);
                holder.checked.setButtonTintList(appearance.radio(current, focused));
            }
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView label;
            final TextView status;
            final RadioButton checked;

            Holder(View view, TextView label, TextView status, RadioButton checked) {
                super(view);
                this.label = label;
                this.status = status;
                this.checked = checked;
            }
        }
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
            row.setMinimumHeight(dp(84));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(params);
            ImageView thumbnail = new ImageView(LauncherSettingsActivity.this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(appearance != null ? appearance.row : 0xFF34204A);
            thumbnail.setContentDescription(null);
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(thumbnail, new LinearLayout.LayoutParams(dp(112), dp(64)));
            LinearLayout words = new LinearLayout(LauncherSettingsActivity.this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wordParams.setMargins(dp(14), 0, dp(8), 0);
            row.addView(words, wordParams);
            TextView label = new TextView(LauncherSettingsActivity.this);
            label.setTextSize(18);
            label.setTextColor(odyssey ? 0xFFFFF2D1 : appearance != null ? appearance.primary : 0xFFFFFFFF);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            words.addView(label, new LinearLayout.LayoutParams(-1, -2));
            TextView status = new TextView(LauncherSettingsActivity.this);
            status.setTextSize(13);
            status.setTextColor(odyssey ? 0xFFD8C7B0 : appearance != null ? appearance.secondary : 0xFFACB8C1);
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
            if (odyssey) holder.itemView.setBackgroundResource(R.drawable.settings_odyssey_choice_bg);
            else if (appearance != null) holder.itemView.setBackground(appearance.rowBackground());
            else holder.itemView.setBackgroundResource(R.drawable.settings_choice_bg);
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
                SharedPreferences.Editor editor = choices.edit();
                if (value == null) editor.remove(Config.WALLPAPER_CHOICE_KEY);
                else editor.putString(Config.WALLPAPER_CHOICE_KEY, value);
                saveChoice(editor);
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
            if (odyssey) {
                holder.label.setTextColor(focused ? 0xFF34204A : 0xFFFFF2D1);
                holder.status.setTextColor(focused ? 0xFF34204A : 0xFFD8C7B0);
                int color = focused ? 0xFF34204A : selected ? 0xFFE1AB35 : 0xFFD8C7B0;
                holder.checked.setButtonTintList(ColorStateList.valueOf(color));
            } else if (appearance != null) {
                holder.label.setTextColor(focused ? appearance.focusText : appearance.primary);
                holder.status.setTextColor(focused ? appearance.focusText : appearance.secondary);
                holder.checked.setButtonTintList(appearance.radio(selected, focused));
            }
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
