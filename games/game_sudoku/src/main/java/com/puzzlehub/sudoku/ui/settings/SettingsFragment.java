package com.puzzlehub.sudoku.ui.settings;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.puzzlehub.sudoku.utils.GameSounds;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.local.SettingsEntity;
import com.puzzlehub.sudoku.game.databinding.FragmentSettingsBinding;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;

public final class SettingsFragment extends Fragment {
    private SettingsViewModel model;
    private boolean rendering;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentSettingsBinding binding = FragmentSettingsBinding.bind(view);
        MainActivity activity = (MainActivity) requireActivity();
        model = new ViewModelProvider(this, new AppViewModelFactory(SudokuRuntime.get(activity)))
                .get(SettingsViewModel.class);

        binding.settingsBack.setOnClickListener(button -> activity.getOnBackPressedDispatcher().onBackPressed());
        binding.settingsGlass.bindBackdrop(activity.getGlassBackdrop());
        binding.settingsGlass.enableLiquidMode(1);
        binding.darkMode.setEnabled(false);
        binding.highlight.setEnabled(false);
        binding.sound.setChecked(GameSounds.isEnabled(requireContext()));
        binding.sound.setOnCheckedChangeListener((button, enabled) -> GameSounds.setEnabled(button.getContext(), enabled));

        rendering = true;
        binding.darkMode.setOnCheckedChangeListener((button, enabled) -> saveDisplay(binding));
        binding.highlight.setOnCheckedChangeListener((button, enabled) -> saveDisplay(binding));
        model.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void saveDisplay(FragmentSettingsBinding binding) {
        if (!rendering) {
            model.save(binding.darkMode.isChecked(), binding.highlight.isChecked());
        }
    }

    private void render(FragmentSettingsBinding binding, SettingsViewModel.State state) {
        SettingsEntity settings = state.settings();
        boolean ready = settings != null && !state.saving();
        if (ready) {
            rendering = true;
            binding.darkMode.setChecked(settings.darkMode);
            binding.highlight.setChecked(settings.highlight);
            rendering = false;
        }
        binding.darkMode.setEnabled(ready);
        binding.highlight.setEnabled(ready);
        if (state.saved() && settings != null) {
            model.consumed();
            int mode = AppCompatDelegate.MODE_NIGHT_NO;
            if (settings.darkMode) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            }
            if (getActivity() instanceof MainActivity host) {
                host.getDelegate().setLocalNightMode(mode);
            }
        }
        if (state.error() != null) {
            AppNotices.show(binding.getRoot(), state.error(), true, false);
            model.consumed();
        }
    }
}
