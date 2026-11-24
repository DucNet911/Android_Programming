package com.puzzlehub.sudoku.ui.profile;

import android.view.View;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.game.databinding.FragmentProfileBinding;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.settings.SettingsFragment;
import com.puzzlehub.sudoku.ui.social.CountryPicker;
import com.puzzlehub.sudoku.ui.social.CountrySelection;
import com.puzzlehub.sudoku.ui.social.FriendsDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;

final class ProfileActions {
    private final Fragment fragment;
    private final FragmentProfileBinding binding;
    private final SudokuRuntime app;

    ProfileActions(Fragment fragment, FragmentProfileBinding binding) {
        this.fragment = fragment;
        this.binding = binding;
        app = SudokuRuntime.get(fragment.requireContext());
    }

    void bind() {
        binding.profileActionToggle.setOnClickListener(button -> {
            boolean expanded = binding.profileQuickActions.getVisibility() == View.VISIBLE;
            if (expanded) {
                binding.profileQuickActions.setVisibility(View.GONE);
                binding.profileActionToggle.setImageResource(R.drawable.ic_profile_expand);
                button.setContentDescription("Mở thao tác hồ sơ");
            } else {
                binding.profileQuickActions.setVisibility(View.VISIBLE);
                binding.profileActionToggle.setImageResource(R.drawable.ic_profile_collapse);
                button.setContentDescription("Thu thao tác hồ sơ");
            }
        });
        if (fragment.requireActivity() instanceof MainActivity activity) {
            binding.openSettings.setOnClickListener(button -> activity.open(new SettingsFragment()));
        } else {
            binding.openSettings.setVisibility(View.GONE);
        }
        binding.signInName.setOnClickListener(button -> new AccountDialogs(fragment, app).show());
        binding.claimHandle.setOnClickListener(button -> new AccountDialogs(fragment, app).showClaim());
        binding.profileCountry.setOnClickListener(button -> CountryPicker.show(fragment,
                app.getProfiles().getState().getValue().getCountry(), "Lưu quốc gia", this::changeCountry));
        binding.friendsMenu.setOnClickListener(this::showFriendsMenu);
    }

    void showCountry(String country) {
        binding.profileCountry.setImageResource(CountryPicker.flag(country));
        binding.profileCountry.setContentDescription(CountryPicker.name(country) + ", đổi quốc gia");
    }

    void renderAccount(PlayerProfile profile) {
        if (profile.getSignedIn()) {
            binding.signInName.setVisibility(View.GONE);
        } else {
            binding.signInName.setVisibility(View.VISIBLE);
        }
        if (!profile.getUsername().isEmpty()) {
            binding.profileHandle.setText("@" + profile.getUsername());
            binding.profileHandle.setVisibility(View.VISIBLE);
        } else {
            binding.profileHandle.setVisibility(View.GONE);
        }
        if (profile.getSignedIn() && !profile.getDemo() && profile.getUsername().isEmpty()
                && app.getUsernameAccounts() != null) {
            binding.claimHandle.setVisibility(View.VISIBLE);
        } else {
            binding.claimHandle.setVisibility(View.GONE);
        }
    }

    private void showFriendsMenu(View anchor) {
        FriendsDialogs dialogs = new FriendsDialogs(fragment);
        PopupMenu menu = new PopupMenu(fragment.requireContext(), anchor);
        menu.getMenu().add("Kết bạn").setOnMenuItemClickListener(item -> {
            dialogs.addFriend(binding.signInName::performClick);
            return true;
        });
        menu.getMenu().add("Xem danh sách bạn bè").setOnMenuItemClickListener(item -> {
            dialogs.showFriends(anchor, binding.signInName::performClick);
            return true;
        });
        menu.show();
    }

    private void changeCountry(String country) {
        if (CountrySelection.save(app, country)) {
            AppNotices.success(binding.getRoot(), "Đã lưu quốc gia");
        }
    }
}
