package com.puzzlehub.sudoku.ui.profile;

import android.app.Dialog;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.data.repository.UsernameAccounts;
import com.puzzlehub.sudoku.game.databinding.DialogPlayerAccountBinding;
import com.puzzlehub.sudoku.ui.social.CountryPicker;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;

final class AccountDialogs {
    private final Fragment fragment;
    private final SudokuRuntime app;
    private DialogPlayerAccountBinding form;
    private Dialog dialog;
    private boolean registering;
    private String country;

    AccountDialogs(Fragment fragment, SudokuRuntime app) {
        this.fragment = fragment;
        this.app = app;
    }

    void show() {
        form = DialogPlayerAccountBinding.inflate(fragment.getLayoutInflater());
        country = app.getProfiles().getState().getValue().getCountry();
        if (country.trim().isEmpty()) {
            country = "VN";
        }
        form.accountCountry.setOnClickListener(view -> CountryPicker.show(fragment, country, "Chọn", selected -> {
            country = selected;
            render();
        }));
        form.switchAccountMode.setOnClickListener(view -> {
            registering = !registering;
            clearErrors();
            render();
        });
        dialog = AppDialogs.form(fragment, "Đăng nhập bằng tên", form.getRoot(), "Đăng nhập", "Hủy", this::submit);
        render();
    }

    void showClaim() {
        UsernameAccounts accounts = app.getUsernameAccounts();
        if (accounts == null) {
            AppNotices.show(fragment.requireView(), "Chưa kết nối được dịch vụ tên người chơi.", true, false);
            return;
        }
        EditText input = new EditText(fragment.requireContext());
        input.setSingleLine(true);
        input.setHint("Ví dụ: linh_sudoku");
        Dialog[] claimDialog = new Dialog[1];
        claimDialog[0] = AppDialogs.form(fragment, "Đặt tên người chơi", input, "Lưu tên", "Hủy", () -> {
            String handle = UsernameAccounts.normalize(String.valueOf(input.getText()));
            if (!UsernameAccounts.isValid(handle) || handle.startsWith("demo_")) {
                input.setError(UsernameAccounts.FORMAT);
                return false;
            }
            View save = claimDialog[0].findViewById(R.id.positive);
            save.setEnabled(false);
            claimDialog[0].setCancelable(false);
            accounts.claim(handle).addOnSuccessListener(created -> {
                app.getProfiles().saveUsername(created);
                claimDialog[0].dismiss();
            }).addOnFailureListener(error -> {
                if (claimDialog[0].isShowing()) {
                    save.setEnabled(true);
                    claimDialog[0].setCancelable(true);
                    input.setError(message(error));
                }
            });
            return false;
        });
        accounts.ownedUsername().addOnSuccessListener(existing -> {
            if (!existing.isEmpty() && claimDialog[0].isShowing()) {
                app.getProfiles().saveUsername(existing);
                claimDialog[0].dismiss();
            }
        });
    }

    private void render() {
        TextView title = dialog.findViewById(R.id.messageTitle);
        MaterialButton action = dialog.findViewById(R.id.positive);
        if (registering) {
            title.setText("Tạo tài khoản");
            action.setText("Tạo tài khoản");
            form.switchAccountMode.setText("Đã có tài khoản? Đăng nhập");
            form.accountCountry.setVisibility(View.VISIBLE);
            form.accountCountry.setText(CountryPicker.name(country) + "  ▾");
        } else {
            title.setText("Đăng nhập bằng tên");
            action.setText("Đăng nhập");
            form.switchAccountMode.setText("Tạo tài khoản");
            form.accountCountry.setVisibility(View.GONE);
        }
    }

    private boolean submit() {
        clearErrors();
        String handle = UsernameAccounts.normalize(String.valueOf(form.usernameInput.getText()));
        String password = String.valueOf(form.passwordInput.getText());
        if (!UsernameAccounts.isValid(handle)) {
            form.usernameField.setError(UsernameAccounts.FORMAT);
            return false;
        }
        if (password.isEmpty() || (registering && password.length() < 8)) {
            form.passwordField.setError(registering ? "Mật khẩu cần ít nhất 8 ký tự." : "Nhập mật khẩu.");
            return false;
        }

        setBusy(true);
        AndroidTasks.onComplete(app.getGames().settings(), state -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    if (!state.activeRoom.trim().isEmpty()) {
                        showError("Hãy rời phòng đang chơi trước khi đổi tài khoản.");
                        return;
                    }
                    connect(handle, password);
                }, error -> showError("Chưa kiểm tra được phòng đang chơi."));
        return false;
    }

    private void connect(String handle, String password) {
        if (handle.startsWith("demo_")) {
            if (registering || !app.getDemoPlayers().signIn(handle, password)) {
                showError("Tên người chơi hoặc mật khẩu chưa đúng.");
                return;
            }
            app.getProfiles().refreshProfile();
            complete("Đăng nhập thành công");
            return;
        }
        UsernameAccounts accounts = app.getUsernameAccounts();
        if (accounts == null) {
            showError("Chưa kết nối được dịch vụ tài khoản.");
            return;
        }
        accounts.authenticate(handle, password, registering).addOnSuccessListener(username -> {
            app.getDemoPlayers().signOut();
            app.getProfiles().saveUsername(username);
            PlayerProfile profile = app.getProfiles().getState().getValue();
            if (profile.getCountry().trim().isEmpty()) {
                app.getProfiles().saveCountry(country);
            }
            publishProfile();
            complete("Đăng nhập thành công");
        }).addOnFailureListener(error -> showError(message(error)));
    }

    private void publishProfile() {
        SocialRepository social = app.getSocial();
        if (social == null || fragment.getView() == null) {
            return;
        }
        PlayerProfile profile = app.getProfiles().getState().getValue();
        social.publishProfile(profile.getName(), profile.getCountry());
    }

    private void complete(String text) {
        form.passwordInput.setText("");
        dialog.dismiss();
        if (fragment.getView() != null) {
            AppNotices.success(fragment.requireView(), text);
        }
    }

    private void showError(String text) {
        if (dialog.isShowing()) {
            setBusy(false);
            form.passwordField.setError(text);
        }
    }

    private void clearErrors() {
        form.usernameField.setError(null);
        form.passwordField.setError(null);
    }

    private void setBusy(boolean busy) {
        dialog.findViewById(R.id.positive).setEnabled(!busy);
        dialog.findViewById(R.id.negative).setEnabled(!busy);
        form.usernameInput.setEnabled(!busy);
        form.passwordInput.setEnabled(!busy);
        form.switchAccountMode.setEnabled(!busy);
        form.accountCountry.setEnabled(!busy);
        dialog.setCancelable(!busy);
    }

    private String message(Exception error) {
        if (error instanceof FirebaseAuthUserCollisionException) {
            return "Tên người chơi này đã được sử dụng.";
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return "Mật khẩu chưa đủ mạnh.";
        }
        if (error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "Chưa đăng nhập được. Kiểm tra tên, mật khẩu và kết nối.";
    }
}
