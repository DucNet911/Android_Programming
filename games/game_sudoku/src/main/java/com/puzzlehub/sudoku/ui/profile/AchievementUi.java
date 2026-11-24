package com.puzzlehub.sudoku.ui.profile;

import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.data.repository.PlayerProgress;
import com.puzzlehub.sudoku.domain.Achievement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AchievementUi {
    public record Badge(Achievement id, String title, String detail, int icon) {
        public Achievement getId() { return id; }
        public String getTitle() { return title; }
        public String getDetail() { return detail; }
        public int getIcon() { return icon; }
    }

    private static final List<Badge> CATALOG = Collections.unmodifiableList(Arrays.asList(
            new Badge(Achievement.FIRE_3, "Giữ lửa", "Mở Sudoku 3 ngày liên tiếp", R.drawable.ic_level_hard),
            new Badge(Achievement.FIRE_7, "Lửa không tắt", "Mở Sudoku 7 ngày liên tiếp", R.drawable.ic_level_hard),
            new Badge(Achievement.FIRST_BEGINNER, "Bước khởi đầu", "Thắng lần đầu ở mức Mới chơi", R.drawable.ic_level_beginner),
            new Badge(Achievement.FIRST_EASY, "Nhẹ nhàng vượt ải", "Thắng lần đầu ở mức Dễ", R.drawable.ic_level_easy),
            new Badge(Achievement.FIRST_MEDIUM, "Vào guồng", "Thắng lần đầu ở mức Bình thường", R.drawable.ic_level_medium),
            new Badge(Achievement.FIRST_HARD, "Chinh phục đỉnh cao", "Thắng lần đầu ở mức Khó", R.drawable.ic_level_hard),
            new Badge(Achievement.PERFECT, "Không tì vết", "Thắng không mắc lỗi, không dùng gợi ý", R.drawable.ic_result_win),
            new Badge(Achievement.TEN_WINS, "Tay chơi Sudoku", "Hoàn thành 10 ván thắng", R.drawable.ic_result_win)));

    private AchievementUi() {}

    public static List<Badge> catalog() { return CATALOG; }

    public static String progress(Achievement badge, PlayerProgress state) {
        return switch (badge) {
            case FIRE_3 -> Math.min(state.getDaily().best(), 3) + "/3 ngày";
            case FIRE_7 -> Math.min(state.getDaily().best(), 7) + "/7 ngày";
            case TEN_WINS -> {
                int total = 0;
                for (int w : state.getWins().values()) {
                    total += w;
                }
                yield Math.min(total, 10) + "/10 ván";
            }
            default -> {
                if (state.getUnlocked().containsKey(badge)) {
                    yield "Đã mở";
                }
                yield "Chưa mở";
            }
        };
    }
}
