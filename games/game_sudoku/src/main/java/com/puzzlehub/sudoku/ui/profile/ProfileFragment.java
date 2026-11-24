package com.puzzlehub.sudoku.ui.profile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.PlayerProgress;
import com.puzzlehub.sudoku.game.databinding.DialogEditNameBinding;
import com.puzzlehub.sudoku.game.databinding.FragmentProfileBinding;
import com.puzzlehub.sudoku.game.databinding.SheetAvatarsBinding;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.history.HistoryFragment;
import com.puzzlehub.sudoku.ui.social.CountryPicker;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.Format;
import java.util.Locale;
import java.util.Map;

public final class ProfileFragment extends Fragment {
    private ProfileViewModel model;
    private BottomSheetDialog avatarSheet;
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    model.importAvatar(uri);
                }
            });
    private final ActivityResultLauncher<PickVisualMediaRequest> coverPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    model.importCover(uri);
                }
            });

    public ProfileFragment() { super(R.layout.fragment_profile); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentProfileBinding binding = FragmentProfileBinding.bind(view);
        MainActivity activity = (MainActivity) requireActivity();
        SudokuRuntime app = SudokuRuntime.get(activity);
        model = new ViewModelProvider(this, new AppViewModelFactory(app)).get(ProfileViewModel.class);
        ProfileActions actions = new ProfileActions(this, binding);
        actions.bind();

        MaterialButton[] levels = {binding.levelBeginner, binding.levelEasy, binding.levelMedium, binding.levelHard};
        Difficulty[] difficulties = Difficulty.values();
        for (int index = 0; index < levels.length; index++) {
            Difficulty difficulty = difficulties[index];
            levels[index].setOnClickListener(button -> model.selectLevel(difficulty));
        }

        binding.changeAvatar.setOnClickListener(button -> {
            if (!Boolean.TRUE.equals(model.getBusy().getValue())) {
                showAvatars();
            }
        });
        binding.editName.setOnClickListener(button -> editName());
        binding.profileBanner.setOnClickListener(button -> {
            if (!Boolean.TRUE.equals(model.getBusy().getValue())) {
                ProfileAppearance.picker(this, profile().background(), model.getProfiles().coverFile() != null,
                        model::background, () -> coverPicker.launch(imageRequest()));
            }
        });
        binding.achievements.setOnClickListener(button -> activity.open(new AchievementsFragment()));
        binding.profileFlame.setOnClickListener(button -> activity.showDailyStreak());
        binding.history.setOnClickListener(button -> activity.open(new HistoryFragment()));
        binding.signIn.setOnClickListener(button -> {
            String country = profile().country();
            if (country.trim().isEmpty()) {
                country = Locale.getDefault().getCountry();
            }
            CountryPicker.show(this, country, "Tiếp tục với Google", selected -> model.signIn(activity, selected));
        });
        binding.signOut.setOnClickListener(button -> AppDialogs.message(this, "Đăng xuất?",
                "Ảnh đại diện của tài khoản được giữ trên thiết bị.", "Đăng xuất", "Ở lại",
                () -> model.signOut(activity), null));

        AndroidTasks.observe(getViewLifecycleOwner(), model.getProfiles().getState(),
                current -> renderProfile(binding, actions, current));
        AndroidTasks.observe(getViewLifecycleOwner(), app.getProgress().getState(),
                progress -> renderProgress(binding, progress));
        model.getLevel().observe(getViewLifecycleOwner(), current -> refreshLevels(levels, difficulties, current));
        model.getStats().observe(getViewLifecycleOwner(), stats -> renderStats(binding, stats));
        model.getBusy().observe(getViewLifecycleOwner(), busy -> setBusy(binding, busy));
        AndroidTasks.observe(getViewLifecycleOwner(), model.getMessage(), feedback -> {
            if (feedback != null) {
                if (feedback.successful()) {
                    AppNotices.success(binding.getRoot(), feedback.text());
                } else {
                    AppNotices.show(binding.getRoot(), feedback.text(), true, false);
                }
                model.consumeMessage();
            }
        });
    }

    private PlayerProfile profile() { return model.getProfiles().getState().getValue(); }

    private void renderProfile(FragmentProfileBinding binding, ProfileActions actions, PlayerProfile current) {
        binding.playerName.setText(current.name());
        binding.accountStatus.setText(current.description());
        visible(binding.accountStatus, !current.description().trim().isEmpty());
        visible(binding.signIn, !current.signedIn());
        visible(binding.signOut, current.signedIn());
        actions.renderAccount(current);
        actions.showCountry(current.country());
        AvatarImages.render(binding.avatar, current.avatar(), model.getProfiles().avatarFile());
        ProfileAppearance.render(binding.profileBanner, binding.coverImage, binding.coverScrim,
                current, model.getProfiles().coverFile());
    }

    private void renderProgress(FragmentProfileBinding binding, PlayerProgress progress) {
        binding.profileFlame.display(progress.daily().current(), progress.daily().best());
        binding.achievements.configure("Thành tựu", "", R.drawable.ic_result_win, R.color.peach, R.color.peach_ink,
                true, progress.unlocked().size() + "/" + AchievementUi.catalog().size() + "  ›");
    }

    private void refreshLevels(MaterialButton[] buttons, Difficulty[] levels, Difficulty selected) {
        for (int index = 0; index < levels.length; index++) {
            Difficulty difficulty = levels[index];
            boolean active = difficulty == selected;
            int tint = R.color.related;
            int text = R.color.muted;
            String label = difficulty.getLabel();
            if (active) {
                tint = R.color.primary;
                text = R.color.background;
                label += ", đang chọn";
            }
            MaterialButton button = buttons[index];
            button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
            button.setTextColor(ContextCompat.getColor(requireContext(), text));
            button.setSelected(active);
            button.setContentDescription(label);
        }
    }

    private void renderStats(FragmentProfileBinding binding, ProfileViewModel.Statistics stats) {
        binding.wins.setText(String.valueOf(stats.wins()));
        binding.perfect.setText(String.valueOf(stats.perfect()));
        binding.streak.setText(String.valueOf(stats.streak()));
        String best = "—:—";
        if (stats.best() != null) {
            best = Format.duration(stats.best());
        }
        binding.bestTime.setText(best);
    }

    private void setBusy(FragmentProfileBinding binding, boolean busy) {
        visible(binding.loading, busy);
        for (View control : new View[]{binding.signIn, binding.signInName, binding.editName, binding.profileCountry,
                binding.friendsMenu, binding.signOut}) {
            control.setEnabled(!busy);
        }
    }

    private void editName() {
        DialogEditNameBinding binding = DialogEditNameBinding.inflate(getLayoutInflater());
        PlayerProfile current = profile();
        binding.nameInput.setText(current.name());
        binding.descriptionLayout.setVisibility(View.VISIBLE);
        binding.descriptionInput.setText(current.description());
        AppDialogs.form(this, "Chỉnh sửa hồ sơ", binding.getRoot(), "Lưu", "Hủy", () -> {
            String name = String.valueOf(binding.nameInput.getText()).trim();
            if (name.trim().isEmpty()) {
                binding.nameLayout.setError("Nhập tên của bạn");
                return false;
            }
            model.updateDetails(name, String.valueOf(binding.descriptionInput.getText()));
            return true;
        });
    }

    private void showAvatars() {
        SheetAvatarsBinding binding = SheetAvatarsBinding.inflate(getLayoutInflater());
        Context context = requireContext();
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        avatarSheet = dialog;
        for (Map.Entry<String, Integer> avatar : AvatarImages.resources().entrySet()) {
            String key = avatar.getKey();
            String name = avatarName(key);
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            column.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            MaterialCardView card = new MaterialCardView(context);
            card.setLayoutParams(new LinearLayout.LayoutParams(dp(62), dp(62)));
            card.setRadius(dp(31));
            card.setCardElevation(0f);
            card.setStrokeWidth(0);
            if (profile().avatar().equals(key)) {
                card.setStrokeWidth(dp(3));
            }
            card.setStrokeColor(ContextCompat.getColor(context, R.color.primary));
            card.setContentDescription("Chọn " + name);
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(view -> {
                model.getProfiles().chooseAvatar(key);
                dialog.dismiss();
            });

            ShapeableImageView image = new ShapeableImageView(context);
            image.setScaleType(ShapeableImageView.ScaleType.CENTER_CROP);
            image.setShapeAppearanceModel(image.getShapeAppearanceModel().toBuilder()
                    .setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
            image.setImageResource(avatar.getValue());
            card.addView(image, new MaterialCardView.LayoutParams(-1, -1));
            column.addView(card);

            TextView label = new TextView(context);
            label.setText(name);
            label.setTextSize(11f);
            label.setTextColor(ContextCompat.getColor(context, R.color.ink));
            label.setPadding(0, dp(8), 0, 0);
            column.addView(label);
            binding.avatarOptions.addView(column);
        }
        binding.uploadPhoto.setOnClickListener(button -> {
            dialog.dismiss();
            photoPicker.launch(imageRequest());
        });
        dialog.setContentView(binding.getRoot());
        dialog.setOnDismissListener(ignored -> avatarSheet = null);
        dialog.show();
    }

    private static PickVisualMediaRequest imageRequest() {
        return new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build();
    }

    private static String avatarName(String key) {
        return switch (key) {
            case "fox" -> "Cáo Cam";
            case "panda" -> "Gấu Mơ";
            case "robot" -> "Bíp Bíp";
            default -> "Mèo Mây";
        };
    }

    private static void visible(View view, boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        if (avatarSheet != null) {
            avatarSheet.dismiss();
            avatarSheet = null;
        }
        super.onDestroyView();
    }
}
