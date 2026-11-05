# Kiến trúc platform mở rộng bằng module

## Platform không biết trước game nào

Project giao cho bạn không có Tetris/2048/Sudoku, không có module `game_tetris`, `game_2048`, `game_sudoku`, không có danh sách đăng ký viết sẵn. Nhóm tự viết từng module khi đến giai đoạn làm game.

| Code | Trách nhiệm |
|---|---|
| `settings.gradle` | Quét thư mục con trực tiếp của `games/`, include module có build.gradle hoặc build.gradle.kts |
| `app/build.gradle` | Tự thêm dependency vào các module được tìm thấy ở games/ |
| `core/GamePlugin.java` | Interface module cần implement và tiền tố metadata |
| `core/GameDefinition.java` | Metadata: ID, tên, mô tả, symbol, màu ARGB, Activity, ranking, độ khó |
| `catalog/GameRegistry.java` | Đọc metadata đã gộp, kiểm tra plugin/Activity, tạo danh sách bất biến |
| `ui/MainActivity.java` | Render danh sách; khi không có plugin hợp lệ thì hiện trạng thái rỗng |
| `ui/GameAdapter.java` | Card dùng chung cho bất kỳ game nào |
| `ui/HistoryActivity.java` | Lịch sử/record dựa trên definition; không có if Tetris/Sudoku |
| `data/ResultRepository.java` | Ghi lịch sử trước, xác nhận kết quả chờ sau |

## Hai thời điểm khác nhau

**Khi build:** thư mục `games/game_<ten>/` chứa Android Library được đưa vào Gradle graph, app dependency và APK. Android gộp Manifest từ module vào Manifest của app.

**Khi chạy:** GameRegistry tìm `<meta-data>` có tiền tố `com.example.arcade.GAME_PLUGIN.` và khởi tạo class GamePlugin trong chính APK. GameDefinition được đưa vào danh sách; mỗi card tự lấy nội dung từ module.

Tạo thư mục trên điện thoại hoặc copy một APK khác vào máy không thêm game vào platform. Cần source module, Sync/Build và bản app mới.

## Biên phụ thuộc

- `app` phụ thuộc `game_core` và các game đã có trong games/.
- Mỗi game phụ thuộc `game_core`; không import app hoặc game khác.
- `game_core` không phụ thuộc app/game và không chứa luật chơi hay tên game cụ thể.
- Class GamePlugin chỉ khai báo metadata, không tải mạng, khởi chạy vòng lặp hay mở cơ sở dữ liệu nặng trong constructor.

GameRegistry bỏ qua plugin sai và ghi lỗi trong Logcat tag **ArcadeRegistry**. ID trùng không được chọn ngẫu nhiên. Lỗi trong một module không ngăn platform mở. Test tích hợp sẽ thất bại nếu còn lỗi đăng ký.

## Lưu dữ liệu

Game tự định nghĩa JSON snapshot. GameSessionStore lưu chuỗi đó mà không hiểu luật chơi, tách theo game/player và cho một checkpoint hoạt động mỗi cặp. Với dữ liệu lớn hơn 100.000 ký tự, game cần dùng file/database và bổ sung cách lưu metadata thích hợp.

Kết thúc: `store.finish(result)` ghi outbox + xóa checkpoint; game trả Activity result; platform ghi SQLite rồi mới xóa outbox. Mất callback vẫn có thể phục hồi khi mở lại trang chủ/lịch sử. Khóa chính game/player/session chống trùng bản ghi.

I/O bản nhỏ còn chạy đồng bộ; với lịch sử/snapshot lớn, chuyển sang executor và phân trang. Cần checkpoint sau nước đi quan trọng; không bảo đảm giữ thao tác chưa lưu khi tiến trình bị dừng đột ngột.

## Tài liệu Android tham khảo

- Metadata: https://developer.android.com/guide/topics/manifest/meta-data-element
- Gộp Manifest: https://developer.android.com/build/manage-manifests
- Activity Result: https://developer.android.com/training/basics/intents/result
