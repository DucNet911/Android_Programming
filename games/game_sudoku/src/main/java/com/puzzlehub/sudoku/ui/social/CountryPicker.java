package com.puzzlehub.sudoku.ui.social;

import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.DialogCountryPickerBinding;
import com.puzzlehub.sudoku.game.databinding.ItemCountryOptionBinding;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import androidx.core.util.Consumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CountryPicker {
    private record Country(String code, String name, @DrawableRes int flag) {}
    private static final List<Country> COUNTRIES = Arrays.asList(
            new Country("VN", "Việt Nam", R.drawable.flag_vn),
            new Country("JP", "Nhật Bản", R.drawable.flag_jp),
            new Country("KR", "Hàn Quốc", R.drawable.flag_kr),
            new Country("US", "Mỹ", R.drawable.flag_us),
            new Country("SG", "Singapore", R.drawable.flag_sg));

    private CountryPicker() {}

    public static String name(String code) {
        if (code.trim().isEmpty()) {
            return "Chọn quốc gia";
        }
        Country known = find(code);
        if (known != null) {
            return known.name();
        }
        return new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag("vi-VN"));
    }

    @DrawableRes
    public static int flag(String code) {
        Country known = find(code);
        if (known != null) {
            return known.flag();
        }
        return R.drawable.ic_country_unset;
    }

    public static void show(Fragment fragment, String previous, String confirm, Consumer<String> onSelected) {
        DialogCountryPickerBinding binding = DialogCountryPickerBinding.inflate(fragment.getLayoutInflater());
        Country initial = find(previous);
        if (initial == null) {
            initial = COUNTRIES.get(0);
        }
        Picker picker = new Picker(binding, initial);
        for (Country country : COUNTRIES) {
            MaterialButton row = ItemCountryOptionBinding.inflate(
                    fragment.getLayoutInflater(), binding.countryOptions, false).getRoot();
            row.setIconResource(country.flag());
            row.setOnClickListener(view -> picker.choose(country));
            picker.rows.put(country, row);
            binding.countryOptions.addView(row);
        }
        picker.render();
        binding.selectedCountry.setOnClickListener(view -> {
            if (binding.countriesScroll.getVisibility() == View.VISIBLE) {
                binding.countriesScroll.setVisibility(View.GONE);
            } else {
                binding.countriesScroll.setVisibility(View.VISIBLE);
            }
        });
        AppDialogs.form(fragment, "Chọn quốc gia", binding.getRoot(), confirm, "Hủy", () -> {
            onSelected.accept(picker.selection.code());
            return true;
        });
    }

    private static Country find(String code) {
        for (Country country : COUNTRIES) {
            if (country.code().equals(code)) {
                return country;
            }
        }
        return null;
    }

    private static final class Picker {
        private final DialogCountryPickerBinding binding;
        private final Map<Country, MaterialButton> rows = new LinkedHashMap<>();
        private Country selection;

        private Picker(DialogCountryPickerBinding binding, Country initial) {
            this.binding = binding;
            this.selection = initial;
        }

        private void choose(Country country) {
            selection = country;
            render();
            binding.countriesScroll.setVisibility(View.GONE);
        }

        private void render() {
            binding.selectedCountry.setText(selection.name() + "  ▾");
            binding.selectedCountry.setIconResource(selection.flag());
            for (Map.Entry<Country, MaterialButton> entry : rows.entrySet()) {
                Country country = entry.getKey();
                MaterialButton row = entry.getValue();
                boolean current = country.equals(selection);
                if (current) {
                    row.setText(country.name() + "  ✓");
                } else {
                    row.setText(country.name());
                }
                row.setSelected(current);
            }
        }
    }
}
