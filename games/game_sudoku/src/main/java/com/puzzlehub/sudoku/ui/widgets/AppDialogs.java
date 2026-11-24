package com.puzzlehub.sudoku.ui.widgets;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.DialogDifficultyBinding;
import com.puzzlehub.sudoku.game.databinding.DialogMessageBinding;
import com.puzzlehub.sudoku.game.databinding.ItemDifficultyBinding;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;

public final class AppDialogs {
    private static final int[] ICONS = {R.drawable.ic_level_beginner, R.drawable.ic_level_easy,
            R.drawable.ic_level_medium, R.drawable.ic_level_hard};
    private static final int[] COLORS = {R.color.mint, R.color.related, R.color.lavender, R.color.peach};
    private static final String[] DETAILS = {"Khởi động nhẹ nhàng", "Chậm rãi, thư thả",
            "Vừa sức, vừa vui", "Thử tài suy luận"};

    private AppDialogs() {}

    public static Dialog difficulty(Fragment fragment, Consumer<Difficulty> onChoose) {
        Difficulty[] levels = Difficulty.values();
        String[] labels = new String[levels.length];
        for (int i = 0; i < labels.length; i++) labels[i] = levels[i].getLabel();
        return levels(fragment, null, labels, DETAILS, index -> onChoose.accept(levels[index]));
    }

    public static Dialog gameModes(Fragment fragment, String summary, String[] labels, Consumer<Integer> onChoose) {
        return levels(fragment, summary, labels, null, onChoose);
    }

    private static Dialog levels(Fragment fragment, String summary, String[] labels, String[] details,
                                 Consumer<Integer> onChoose) {
        DialogDifficultyBinding binding = DialogDifficultyBinding.inflate(fragment.getLayoutInflater());
        if (summary != null) binding.modeSummary.setText(summary);
        Dialog dialog = prepare(fragment, binding.getRoot(), true);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            ItemDifficultyBinding row = ItemDifficultyBinding.inflate(fragment.getLayoutInflater(), binding.levelOptions, false);
            row.levelTitle.setText(labels[i]);
            if (details == null) row.levelDetail.setVisibility(View.GONE);
            else row.levelDetail.setText(details[i]);
            row.levelIcon.setImageResource(ICONS[i % ICONS.length]);
            int tint = ContextCompat.getColor(fragment.requireContext(), COLORS[i % COLORS.length]);
            row.getRoot().setCardBackgroundColor(ColorUtils.setAlphaComponent(tint, 150));
            GradientDrawable badge = new GradientDrawable();
            badge.setColor(tint);
            badge.setCornerRadius(dp(fragment, 14));
            row.iconBadge.setBackground(badge);
            row.getRoot().setContentDescription(labels[i]);
            row.getRoot().setOnClickListener(view -> {
                dialog.dismiss();
                onChoose.accept(index);
            });
            binding.levelOptions.addView(row.getRoot());
        }
        binding.close.setOnClickListener(view -> dialog.dismiss());
        present(fragment, dialog, binding.getRoot());
        return dialog;
    }

    public static Dialog message(Fragment fragment, CharSequence title, CharSequence body,
                                 CharSequence positive, int icon) {
        return message(fragment, title, body, positive, null, icon, true, null, null, false);
    }

    public static Dialog message(Fragment fragment, CharSequence title, CharSequence body,
                                 CharSequence positive, CharSequence negative, Runnable onPositive, Runnable onNegative) {
        return message(fragment, title, body, positive, negative, R.drawable.ic_notice_info,
                true, onPositive, onNegative, false);
    }

    public static Dialog message(Fragment fragment, CharSequence title, CharSequence body,
                                 CharSequence positive, CharSequence negative, int icon, boolean cancelable,
                                 Runnable onPositive, Runnable onNegative, boolean celebrate) {
        return build(fragment, title, body, positive, negative, icon, cancelable, null, () -> {
            if (onPositive != null) {
                onPositive.run();
            }
            return true;
        }, onNegative, celebrate);
    }

    public static Dialog form(Fragment fragment, CharSequence title, View content, CharSequence positive,
                              CharSequence negative, Supplier<Boolean> submit) {
        return build(fragment, title, "", positive, negative, R.drawable.ic_profile, true, content, submit, null, false);
    }

    public static Dialog content(Fragment fragment, CharSequence title, View content, CharSequence close) {
        return build(fragment, title, "", close, null, R.drawable.ic_notice_info, true, content, () -> true, null, false);
    }

    private static Dialog build(Fragment fragment, CharSequence title, CharSequence body, CharSequence positive,
                                CharSequence negative, int icon, boolean cancelable, View content,
                                Supplier<Boolean> submit, Runnable onNegative, boolean celebrate) {
        DialogMessageBinding binding = DialogMessageBinding.inflate(fragment.getLayoutInflater());
        binding.messageTitle.setText(title);
        binding.messageBody.setText(body);
        binding.messageIcon.setImageResource(icon);
        if (icon == R.drawable.ic_result_win || icon == R.drawable.ic_level_hard || icon == R.drawable.ic_sudoku) {
            binding.messageIcon.setImageTintList(null);
        }
        if (content != null) {
            binding.messageScroll.setVisibility(View.GONE);
            binding.customContent.setVisibility(View.VISIBLE);
            binding.customContent.addView(content);
        } else {
            int width = dialogWidth(fragment) - dp(fragment, 44);
            binding.messageBody.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int screenLimit = (int) (fragment.getResources().getDisplayMetrics().heightPixels * 0.38f);
            binding.messageScroll.getLayoutParams().height = Math.min(binding.messageBody.getMeasuredHeight(),
                    Math.min(dp(fragment, 250), screenLimit));
        }
        Dialog dialog = prepare(fragment, binding.getRoot(), cancelable);
        binding.positive.setText(positive);
        binding.negative.setText(negative);
        if (negative == null) {
            binding.negative.setVisibility(View.GONE);
        } else {
            binding.negative.setVisibility(View.VISIBLE);
        }
        binding.positive.setOnClickListener(view -> {
            if (submit.get()) {
                dialog.dismiss();
            }
        });
        binding.negative.setOnClickListener(view -> {
            dialog.dismiss();
            if (onNegative != null) {
                onNegative.run();
            }
        });
        present(fragment, dialog, binding.getRoot());
        if (celebrate) {
            CelebrationOverlay.showOver(binding.getRoot());
        }
        return dialog;
    }

    private static Dialog prepare(Fragment fragment, FrostedGlassLayout surface, boolean cancelable) {
        surface.bindBackdrop(fragment.requireActivity().getWindow().getDecorView());
        surface.enableLiquidMode(4);
        surface.setGlassTint(ContextCompat.getColor(fragment.requireContext(), R.color.modal_glass));
        Dialog dialog = new Dialog(fragment.requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(surface);
        dialog.setCancelable(cancelable);

        Lifecycle lifecycle = fragment.getViewLifecycleOwner().getLifecycle();
        DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
            @Override public void onDestroy(LifecycleOwner owner) { dialog.dismiss(); }
        };
        lifecycle.addObserver(observer);
        dialog.setOnDismissListener(ignored -> lifecycle.removeObserver(observer));
        return dialog;
    }

    private static void present(Fragment fragment, Dialog dialog, View surface) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.32f);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(dialogWidth(fragment), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) {
            surface.setAlpha(0f);
            surface.setTranslationY(dp(fragment, 8));
            surface.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        }
    }

    private static int dialogWidth(Fragment fragment) {
        return Math.min(fragment.getResources().getDisplayMetrics().widthPixels - dp(fragment, 32), dp(fragment, 400));
    }

    private static int dp(Fragment fragment, int value) {
        return (int) (value * fragment.getResources().getDisplayMetrics().density);
    }
}
