package com.puzzlehub.sudoku.ui.multiplayer;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.data.model.OnlinePlayer;
import com.puzzlehub.sudoku.data.model.OnlineRoom;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.databinding.DialogMessageBinding;
import com.puzzlehub.sudoku.ui.MainActivity;
import androidx.core.util.Consumer;
import java.util.Collections;
import java.util.List;

public final class RoomCodeDialog {
    private RoomCodeDialog() {}

    public static final class WaitingRoomPopup {
        private final DialogMessageBinding binding;
        private final PopupViews views;
        private final FrameLayout overlay;
        private final Dialog dialog;

        WaitingRoomPopup(DialogMessageBinding binding, PopupViews views, FrameLayout overlay, Dialog dialog) {
            this.binding = binding;
            this.views = views;
            this.overlay = overlay;
            this.dialog = dialog;
        }

        public boolean isShowing() {
            return (overlay != null && overlay.getVisibility() == View.VISIBLE)
                    || (dialog != null && dialog.isShowing());
        }

        public void render(OnlineRoom room, boolean host, boolean busy) {
            Context context = binding.getRoot().getContext();
            boolean guestJoined = room.players().size() >= 2 || "READY".equals(room.status());
            views.codeTextView().setText(room.code());
            views.codeTextView().setContentDescription("Mã phòng " + room.code());
            OnlinePlayer hostPlayer = null;
            OnlinePlayer guest = null;
            for (OnlinePlayer player : room.players()) {
                if (player.uid().equals(room.hostId())) {
                    hostPlayer = player;
                    break;
                }
            }
            if (hostPlayer == null && !room.players().isEmpty()) {
                hostPlayer = room.players().get(0);
            }
            if (hostPlayer != null) {
                for (OnlinePlayer player : room.players()) {
                    if (!player.uid().equals(hostPlayer.uid())) {
                        guest = player;
                        break;
                    }
                }
            }

            binding.messageTitle.setText("Phòng chờ");
            views.guestSpinner().setVisibility(View.VISIBLE);
            if (guestJoined) {
                views.guestStatusView().setTextColor(ContextCompat.getColor(context, R.color.ink));
                views.guestStatusView().setTypeface(null, Typeface.BOLD);
                if (host) {
                    binding.messageTitle.setText("Đã đủ người chơi");
                    views.guestSpinner().setVisibility(View.GONE);
                    String guestName = "Người chơi 2";
                    if (guest != null) {
                        guestName = guest.name();
                    }
                    views.guestStatusView().setText(guestName);
                } else {
                    String hostName = "Chủ phòng";
                    if (hostPlayer != null) {
                        hostName = hostPlayer.name();
                    }
                    views.guestStatusView().setText(hostName + " · Chờ bắt đầu...");
                }
            } else {
                views.guestStatusView().setText("Đang chờ người khác vào phòng...");
                views.guestStatusView().setTextColor(ContextCompat.getColor(context, R.color.muted));
                views.guestStatusView().setTypeface(null, Typeface.NORMAL);
            }

            views.copyButton().setEnabled(true);
            binding.negative.setEnabled(true);
            if (host) {
                binding.negative.setText("Hủy phòng");
                binding.positive.setVisibility(View.VISIBLE);
                binding.positive.setText("Bắt đầu");
                boolean canStart = guestJoined && !busy;
                binding.positive.setEnabled(canStart);
                if (canStart) {
                    binding.positive.setAlpha(1f);
                } else {
                    binding.positive.setAlpha(0.45f);
                }
            } else {
                binding.negative.setText("Rời phòng");
                binding.positive.setVisibility(View.GONE);
            }
        }

        public void dismissQuietly() {
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
                overlay.removeAllViews();
            }
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    public static WaitingRoomPopup showInOverlay(Fragment fragment, FrameLayout overlay, OnlineRoom room,
            boolean host, boolean busy, Consumer<String> onCopy, Runnable onStart, Runnable onCancel) {
        Context context = fragment.requireContext();
        DialogMessageBinding binding = DialogMessageBinding.inflate(fragment.getLayoutInflater());
        PopupViews views = populate(context, binding, room.code(), onCopy);
        View backdrop = fragment.requireActivity().getWindow().getDecorView();
        if (fragment.getActivity() instanceof MainActivity main && main.getGlassBackdrop() != null) {
            backdrop = main.getGlassBackdrop();
        }
        glass(binding, backdrop);
        int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 40), dp(context, 400));
        overlay.removeAllViews();
        overlay.addView(binding.getRoot(), new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.setVisibility(View.VISIBLE);
        WaitingRoomPopup popup = new WaitingRoomPopup(binding, views, overlay, null);
        binding.positive.setOnClickListener(ignored -> onStart.run());
        binding.negative.setOnClickListener(ignored -> {
            popup.dismissQuietly();
            onCancel.run();
        });
        popup.render(room, host, busy);
        return popup;
    }

    public static Dialog show(Fragment fragment, String code, Consumer<String> onCopy) {
        Context context = fragment.requireContext();
        DialogMessageBinding binding = DialogMessageBinding.inflate(fragment.getLayoutInflater());
        PopupViews views = populate(context, binding, code, onCopy);
        glass(binding, fragment.requireActivity().getWindow().getDecorView());
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        dialog.setCancelable(true);
        SudokuBoard empty = SudokuBoard.empty();
        OnlineRoom example = new OnlineRoom(code, "COMPETITIVE", Difficulty.BEGINNER.name(), "WAITING",
                empty, empty, 0, Collections.emptyList(), 0, "", 0, 0, null, "", "");
        WaitingRoomPopup popup = new WaitingRoomPopup(binding, views, null, dialog);
        binding.negative.setOnClickListener(ignored -> popup.dismissQuietly());
        popup.render(example, true, false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.36f;
            window.setAttributes(attributes);
        }
        dialog.show();
        if (window != null) {
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 32), dp(context, 400));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static void glass(DialogMessageBinding binding, View backdrop) {
        binding.getRoot().bindBackdrop(backdrop);
        binding.getRoot().enableLiquidMode(4);
        binding.getRoot().setGlassTint(ContextCompat.getColor(binding.getRoot().getContext(), R.color.modal_glass));
    }

    private record PopupViews(TextView codeTextView, MaterialButton copyButton,
                              ProgressBar guestSpinner, TextView guestStatusView) {}

    private static PopupViews populate(Context context, DialogMessageBinding binding,
                                       String initialCode, Consumer<String> onCopy) {
        binding.messageIcon.setImageResource(R.drawable.ic_people);
        binding.messageScroll.setVisibility(View.GONE);
        binding.customContent.setVisibility(View.VISIBLE);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout codeCard = new LinearLayout(context);
        codeCard.setOrientation(LinearLayout.VERTICAL);
        codeCard.setGravity(Gravity.CENTER_HORIZONTAL);
        codeCard.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_soft));
        codeCard.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        container.addView(codeCard, row(context, 6));

        TextView code = new TextView(context);
        code.setText(initialCode);
        code.setGravity(Gravity.CENTER);
        code.setTextSize(28);
        code.setLetterSpacing(0.12f);
        code.setTypeface(code.getTypeface(), Typeface.BOLD);
        code.setTextColor(ContextCompat.getColor(context, R.color.primary));
        code.setPadding(0, dp(context, 2), 0, dp(context, 8));
        codeCard.addView(code);

        MaterialButton copy = new MaterialButton(context);
        copy.setText("Chép mã phòng");
        copy.setTextSize(12);
        copy.setMinHeight(0);
        copy.setMinimumHeight(0);
        copy.setInsetTop(0);
        copy.setInsetBottom(0);
        copy.setCornerRadius(dp(context, 14));
        copy.setElevation(0);
        copy.setStateListAnimator(null);
        copy.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface)));
        copy.setTextColor(ContextCompat.getColor(context, R.color.primary));
        copy.setStrokeWidth(dp(context, 1));
        copy.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grid)));
        copy.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        copy.setOnClickListener(ignored -> onCopy.accept(code.getText().toString()));
        codeCard.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 36)));

        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 14));
        background.setColor(ContextCompat.getColor(context, R.color.related));
        status.setBackground(background);
        status.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        ProgressBar spinner = new ProgressBar(context);
        LinearLayout.LayoutParams spinnerSize = new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
        spinnerSize.setMarginEnd(dp(context, 10));
        status.addView(spinner, spinnerSize);
        TextView guestStatus = new TextView(context);
        guestStatus.setTextSize(14);
        status.addView(guestStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        container.addView(status, row(context, 12));
        binding.customContent.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new PopupViews(code, copy, spinner, guestStatus);
    }

    private static LinearLayout.LayoutParams row(Context context, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topMargin);
        return params;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
