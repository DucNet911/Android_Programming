# Phân công cho nhóm

| Người | Phần sở hữu | Bàn giao |
|---|---|---|
| Bạn | app, game_core, cấu hình root, tài liệu | Platform 0 game; API ổn định; nhận/tích hợp module |
| Thành viên 1 | games/game_tetris do họ tự tạo | Engine, UI, snapshot, plugin, Manifest, test Tetris |
| Thành viên 2 | games/game_2048 do họ tự tạo | Engine, UI, snapshot, plugin, Manifest, test 2048 |
| Thành viên 3 | games/game_sudoku do họ tự tạo | Engine, UI, snapshot, plugin, Manifest, test Sudoku |

Mỗi người clone toàn bộ project để chạy thử bằng app, nhưng chỉ sửa module của mình. Các thư mục game trong bảng chưa có trong bản bàn giao.

## Người làm platform

- Chốt Java 17/XML, min23/compile35, contract v1 và cách đặt tên module.
- Nhận PR từng game, kiểm tra nguồn và dependency trước khi build.
- Chạy test/lint và kiểm tra Activity/module đăng ký đúng; không viết lại giao diện khi thêm game.
- Khi thay hợp đồng core, trao đổi và cập nhật đồng bộ các thành viên.
- Các chức năng ngoài phạm vi hiện tại như tài khoản/backend làm ở mốc riêng nếu đề bài yêu cầu.

## Người làm game

1. Tự tạo Android Library theo tài liệu 02.
2. Viết engine Java độc lập khỏi Activity để kiểm thử luật chơi.
3. Viết giao diện XML/View, xử lý tương tác và vòng đời.
4. Implement GamePlugin, khai báo metadata/Activity và tài nguyên có tiền tố riêng.
5. Thực hiện NEW_GAME, RESUME, checkpoint, FINISHED, SAVED_FOR_LATER, ABANDONED.
6. Kiểm tra riêng luật chơi và kiểm tra kết nối với platform.

## Tiêu chí nhận module

- Module nằm đúng games/game_<ten>, không import app hoặc game khác.
- Không thêm launcher/Application riêng làm thay ứng dụng chủ; Activity exported=false.
- ID runtime/metadata trùng và không xung đột module khác.
- Không gọi mạng/loop nặng khi khám phá plugin.
- Card tự xuất hiện sau Sync/Build, tên/độ khó lấy từ module.
- Lưu/tiếp tục giữ trạng thái, UUID và thời gian; lỗi resume không âm thầm tạo ván mới.
- Trả kết quả đúng loại; không ghi ván trùng hoặc tính thời gian nền.
- Module có tests engine và đã chạy build/test/lint toàn project.

Ví dụ luật cần test: Tetris va chạm/xóa hàng/game over; 2048 gộp mỗi ô một lần và không sinh ô khi lượt không đổi bàn; Sudoku khóa ô đề/đề có nghiệm duy nhất/điều kiện hoàn thành. Platform không thể tự xác minh toàn bộ luật chỉ bằng class GamePlugin.
