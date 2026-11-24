package com.puzzlehub.sudoku.ui.social;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.Friendship;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.data.repository.SocialPlayer;
import com.puzzlehub.sudoku.data.repository.DemoPlayers;
import com.puzzlehub.sudoku.data.repository.UsernameAccounts;
import com.puzzlehub.sudoku.game.databinding.DialogFriendCodeBinding;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;

import androidx.core.util.Consumer;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.List;
import java.util.concurrent.TimeoutException;

public final class FriendsDialogs {
    private final Fragment fragment;

    public FriendsDialogs(Fragment fragment) {
        this.fragment = fragment;
    }

    private SudokuRuntime app() {
        return SudokuRuntime.get(fragment.requireContext());
    }

    public void addFriend(Runnable signIn) {
        DialogFriendCodeBinding form = DialogFriendCodeBinding.inflate(fragment.getLayoutInflater());
        SocialRepository social = app().getSocial();
        PlayerProfile profile = profile();
        if (!profile.getSignedIn()) {
            form.friendCodeInput.setEnabled(false);
            form.friendCodeNotice.setVisibility(View.VISIBLE);
            form.friendCodeNotice.setText("Đăng nhập để tìm và kết bạn.");
            AppDialogs.form(fragment, "Tìm bạn", form.getRoot(), "Đăng nhập", "Đóng", () -> {
                fragment.requireView().post(signIn);
                return true;
            });
        } else if (profile.getDemo()) {
            showSearch(form, null);
        } else if (social == null) {
            message("Tìm bạn", "Chưa kết nối được dịch vụ bạn bè.", "Đóng", null, null, null);
        } else {
            withCountry(social, () -> showSearch(form, social));
        }
    }

    private void showSearch(DialogFriendCodeBinding form, SocialRepository social) {
        useNames(form);
        Dialog[] dialog = new Dialog[1];
        boolean[] searchingByCode = {false};
        dialog[0] = AppDialogs.form(fragment, "Tìm bạn", form.getRoot(), "Tìm người chơi", "Hủy", () -> {
            String input = String.valueOf(form.friendCodeInput.getText()).trim();
            if (searchingByCode[0]) {
                if (input.trim().isEmpty()) {
                    form.friendCodeField.setError("Nhập mã bạn bè.");
                } else {
                    sendByCode(form, dialog[0], social, input);
                }
            } else {
                searchName(form, dialog[0], social, UsernameAccounts.normalize(input));
            }
            return false;
        });

        if (social != null) {
            form.useFriendCode.setVisibility(View.VISIBLE);
            form.copyOwnFriendCode.setVisibility(View.VISIBLE);
            form.copyOwnFriendCode.setOnClickListener(button -> copyOwnCode(social, form.copyOwnFriendCode));
            form.useFriendCode.setOnClickListener(button -> {
                searchingByCode[0] = !searchingByCode[0];
                form.friendCodeField.setError(null);
                form.friendCodeInput.setText("");
                TextView action = dialog[0].findViewById(R.id.positive);
                if (searchingByCode[0]) {
                    form.friendCodeField.setHint("Mã bạn bè");
                    form.useFriendCode.setText("Tìm theo tên người chơi");
                    action.setText("Gửi lời mời");
                } else {
                    useNames(form);
                    action.setText("Tìm người chơi");
                }
            });
        }
    }

    private void useNames(DialogFriendCodeBinding form) {
        form.friendCodeField.setHint("Tên người chơi");
        form.useFriendCode.setText("Nhập mã bạn bè");
    }

    private void searchName(DialogFriendCodeBinding form, Dialog dialog, SocialRepository social, String handle) {
        if (!UsernameAccounts.isValid(handle)) {
            form.friendCodeField.setError(UsernameAccounts.FORMAT);
            return;
        }
        if (handle.equals(profile().getUsername())) {
            form.friendCodeField.setError("Đây là tên của bạn.");
            return;
        }
        if (social == null) {
            SocialPlayer player = DemoPlayers.find(handle);
            if (player == null) {
                form.friendCodeField.setError("Không tìm thấy tên người chơi này.");
                return;
            }
            dialog.dismiss();
            message("@" + handle, player.getName() + " · " + CountryPicker.name(player.getCountry()), "Gửi lời mời", "Đóng", () -> {
                if (app().getDemoPlayers().invite(handle)) {
                    AppNotices.success(fragment.requireView(), "Đã gửi lời mời kết bạn");
                } else {
                    AppNotices.show(fragment.requireView(), "Hai bạn đã có lời mời hoặc đã kết bạn.", true, false);
                }
            }, null);
            return;
        }
        UsernameAccounts accounts = app().getUsernameAccounts();
        if (accounts == null) {
            form.friendCodeField.setError("Chưa kết nối được dịch vụ tìm bạn.");
            return;
        }

        View find = dialog.findViewById(R.id.positive);
        find.setEnabled(false);
        accounts.findUid(handle).addOnSuccessListener(uid -> {
            if (!dialog.isShowing()) {
                return;
            }
            find.setEnabled(true);
            if (uid.equals(social.getPlayerId())) {
                form.friendCodeField.setError("Đây là tên của bạn.");
                return;
            }
            dialog.dismiss();
            message("@" + handle, "Gửi lời mời kết bạn cho người chơi này?", "Gửi lời mời", "Đóng",
                    () -> publish(social, () -> update(social.requestFriend(uid),
                            "Đã gửi lời mời kết bạn"), this::notice), null);
        }).addOnFailureListener(error -> {
            if (dialog.isShowing()) {
                find.setEnabled(true);
                form.friendCodeField.setError(errorText(error));
            }
        });
    }

    private void sendByCode(DialogFriendCodeBinding form, Dialog dialog, SocialRepository social, String code) {
        View submit = dialog.findViewById(R.id.positive);
        submit.setEnabled(false);
        form.friendCodeField.setError(null);
        Consumer<Throwable> failed = error -> {
            form.friendCodeField.setError(errorText(error));
            submit.setEnabled(true);
        };
        publish(social, () -> task(social.requestFriend(code), () -> {
            dialog.dismiss();
            AppNotices.success(fragment.requireView(), "Đã gửi lời mời kết bạn");
        }, failed), failed);
    }

    private void copyOwnCode(SocialRepository social, TextView button) {
        String player = social.getPlayerId();
        if (player == null) {
            return;
        }
        button.setEnabled(false);
        publish(social, () -> {
            ClipboardManager clipboard = fragment.requireContext().getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Mã bạn bè", player));
                button.setText("Đã sao chép mã của tôi");
            }
            button.setEnabled(true);
        }, error -> {
            button.setEnabled(true);
            notice(error);
        });
    }

    public void showFriends(View anchor, Runnable signIn) {
        if (!profile().getSignedIn()) {
            message("Danh sách bạn bè", "Đăng nhập để xem bạn bè và lời mời.",
                    "Đăng nhập", "Đóng", () -> fragment.requireView().post(signIn), null);
            return;
        }
        if (profile().getDemo()) {
            showRelations(null, app().getDemoPlayers().friends());
            return;
        }
        SocialRepository social = app().getSocial();
        if (social == null) {
            message("Danh sách bạn bè", "Chưa kết nối được dịch vụ bạn bè.", "Đóng", null, null, null);
            return;
        }

        withCountry(social, () -> {
            Dialog loading = message("Danh sách bạn bè", "Đang tải bạn bè và lời mời…", "Đóng", null, null, null);
            anchor.setEnabled(false);
            Consumer<Throwable> failed = error -> {
                anchor.setEnabled(true);
                if (loading.isShowing()) {
                    loading.dismiss();
                    message("Danh sách bạn bè", errorText(error), "Đóng", null, null, null);
                }
            };
            publish(social, () -> AndroidTasks.onComplete(AndroidTasks.withTimeout(social.friends(), SocialRepository.NETWORK_TIMEOUT), relations -> {
                        anchor.setEnabled(true);
                        if (loading.isShowing()) {
                            loading.dismiss();
                            showRelations(social, relations);
                        }
                    }, failed), failed);
        });
    }

    private void showRelations(SocialRepository social, List<Friendship> relations) {
        if (relations.isEmpty()) {
            message("Danh sách bạn bè", "Chia sẻ mã của bạn hoặc nhập mã của một người bạn.",
                    "Đóng", null, null, null);
            return;
        }
        CharSequence[] labels = new CharSequence[relations.size()];
        for (int index = 0; index < relations.size(); index++) {
            Friendship relation = relations.get(index);
            String status;
            if (relation.getStatus().equals(SocialRepository.ACCEPTED)) {
                status = "Bạn bè";
            } else if (relation.getIncoming()) {
                status = "Lời mời đến";
            } else {
                status = "Đang chờ chấp nhận";
            }
            labels[index] = relation.getPlayer().getName() + " · " + status;
        }

        Dialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Danh sách bạn bè")
                .setItems(labels, (window, index) -> manageRelation(social, relations.get(index)))
                .setNegativeButton("Đóng", null)
                .show();
        LifecycleOwner owner = fragment.getViewLifecycleOwner();
        DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner source) {
                dialog.dismiss();
            }
        };
        owner.getLifecycle().addObserver(observer);
        dialog.setOnDismissListener(window -> owner.getLifecycle().removeObserver(observer));
    }

    private void manageRelation(SocialRepository social, Friendship relation) {
        String name = relation.getPlayer().getName();
        String key = relation.getKey();
        if (social == null) {
            if (relation.getIncoming() && relation.getStatus().equals(SocialRepository.PENDING)) {
                message(name, "Bạn có muốn kết bạn?", "Chấp nhận", "Từ chối",
                        () -> app().getDemoPlayers().updateFriend(key, true),
                        () -> app().getDemoPlayers().updateFriend(key, false));
            } else {
                message(name, "Bạn có muốn xóa kết nối này?", "Xóa", "Giữ lại",
                        () -> app().getDemoPlayers().updateFriend(key, false), null);
            }
            return;
        }
        if (relation.getStatus().equals(SocialRepository.ACCEPTED)) {
            message("Xóa bạn bè?", name, "Xóa bạn", "Giữ lại",
                    () -> update(social.removeFriend(key), "Đã xóa bạn"), null);
        } else if (relation.getIncoming()) {
            message(name, "Bạn có muốn kết bạn?", "Chấp nhận", "Từ chối",
                    () -> update(social.acceptFriend(key), "Đã kết bạn"),
                    () -> update(social.removeFriend(key), "Đã từ chối lời mời"));
        } else {
            message("Hủy lời mời?", name, "Hủy lời mời", "Giữ lại",
                    () -> update(social.removeFriend(key), "Đã hủy lời mời"), null);
        }
    }

    private void update(SudokuTask<Void> change, String confirmation) {
        task(change, () -> AppNotices.success(fragment.requireView(), confirmation), this::notice);
    }

    private PlayerProfile profile() {
        return app().getProfiles().getState().getValue();
    }

    private void withCountry(SocialRepository social, Runnable ready) {
        PlayerProfile profile = profile();
        if (!profile.getCountry().trim().isEmpty()) {
            ready.run();
            return;
        }
        CountryPicker.show(fragment, "", "Lưu quốc gia", country -> task(
                social.publishProfile(profile.getName(), country),
                () -> {
                    app().getProfiles().saveCountry(country);
                    ready.run();
                }, this::notice));
    }

    private void publish(SocialRepository social, Runnable success, Consumer<Throwable> error) {
        PlayerProfile profile = profile();
        task(social.publishProfile(profile.getName(), profile.getCountry()), success, error);
    }

    private void task(SudokuTask<Void> operation, Runnable done, Consumer<Throwable> failed) {
        AndroidTasks.onComplete(AndroidTasks.withTimeout(operation, SocialRepository.NETWORK_TIMEOUT), ignored -> {
            if (fragment.getView() != null) {
                done.run();
            }
        }, error -> {
            if (fragment.getView() != null) {
                failed.accept(error);
            }
        });
    }

    private void notice(Throwable error) {
        AppNotices.show(fragment.requireView(), errorText(error), true, false);
    }

    private String errorText(Throwable error) {
        if (error instanceof TimeoutException) {
            return "Kết nối quá lâu. Thử lại nhé.";
        }
        if ((error instanceof IllegalArgumentException || error instanceof IllegalStateException)
                && error.getMessage() != null) {
            return error.getMessage();
        }
        return "Chưa cập nhật được. Thử lại nhé.";
    }

    private Dialog message(String title, String body, String positive, String negative,
                           Runnable onPositive, Runnable onNegative) {
        return AppDialogs.message(fragment, title, body, positive, negative, R.drawable.ic_notice_info,
                true, () -> {
                    if (onPositive != null) {
                        onPositive.run();
                    }

                }, () -> {
                    if (onNegative != null) {
                        onNegative.run();
                    }

                }, false);
    }
}
