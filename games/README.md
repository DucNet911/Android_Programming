# Thư mục nhận game

Ban đầu thư mục này không chứa module/game nào.

Mỗi thành viên thêm Android Library của mình tại `games/game_<ten>/`, gồm `build.gradle`, `src/main/AndroidManifest.xml`, code Java, layout XML và tài nguyên riêng.

Gradle tự phát hiện module sau Sync. Platform đọc khai báo `GamePlugin` trong Manifest đã gộp để tạo card. Không thêm dòng vào registry hoặc sửa giao diện app.

Xem `../docs/02_THEM_GAME.md` và `../docs/03_HOP_DONG_GAME_CORE.md` trước khi bắt đầu.
