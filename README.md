# Arcade Platform

Ứng dụng Android viết bằng Java và XML, tích hợp ba trò chơi trên trang chủ. Người chơi có thể tìm game, đánh dấu yêu thích, tiếp tục ván đã lưu và xem lịch sử kết quả.

## Trò chơi hiện có

| Trò chơi | Cách chơi |
|---|---|
| [2048](games/game_2048/) | Vuốt để dồn các ô số. Hai ô cùng giá trị ghép thành một ô có giá trị gấp đôi. Mục tiêu là tạo ô 2048. |
| [Sudoku](games/game_sudoku/) | Điền các số từ 1 đến 9 sao cho mỗi hàng, cột và vùng 3×3 đều có đủ chín số khác nhau. |
| [Tetris](games/game_tetris/) | Di chuyển và xoay các khối đang rơi để lấp kín hàng ngang và ghi điểm. |

Trong mục **Khám phá** của Sudoku còn có hai trò chơi nhỏ. **Bomber Kho Tinh Thể** cho phép người chơi đặt bom theo lượt để phá thùng và thu thập tinh thể. **Mạch màu** có hai lựa chọn là đề ngẫu nhiên và đề hôm nay.

## Chạy platform

1. Mở thư mục chứa `settings.gradle` bằng Android Studio.
2. Chọn **Gradle JDK 17**; cài Android SDK Platform 35 và Build Tools 35.0.0.
3. Sync Gradle, chọn module `app`, chạy trên Android 6.0+ (API 23+).

Windows:

```powershell
.\gradlew.bat :app:assembleDebug testDebugUnitTest lintDebug
```

Linux/macOS:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug testDebugUnitTest lintDebug
```

Bộ cấu hình giữ cố định: Java 17, Gradle 8.11.1, AGP 8.9.2, compile/target SDK 35, min SDK 23. Không nâng riêng wrapper sang Gradle 9 khi chưa nâng đồng bộ công cụ.

## Thành viên thêm game như thế nào?

1. Tự viết **Android Library** trong `games/game_<ten>/` theo [hướng dẫn tích hợp](docs/02_THEM_GAME.md).
2. Implement `GamePlugin` và khai báo class đó trong Manifest **của module game**. Module tự cung cấp tên, mô tả, màu, biểu tượng chữ, Activity, các độ khó và loại thành tích.
3. Copy module vào `games/`, **Sync → Build → chạy bản app mới**.

Gradle tự thêm module dưới `games/`. Manifest được gộp khi build. Platform đọc `GamePlugin` và tự tạo card. Không sửa Java/XML của app, không thêm case cho từng game vào registry, không sửa danh sách game trong core.

Đây là thêm game bằng source ở thời điểm build. Người dùng điện thoại nhận game mới qua bản cập nhật APK; ứng dụng không tải/chạy file game tùy ý từ Internet.

## Cấu trúc dự án

| Thành phần | Vai trò |
|---|---|
| `app` | Trang chủ, danh sách rỗng/có game, tìm kiếm, yêu thích, điều hướng, lịch sử và thành tích |
| `game_core` | API đăng ký game, request, checkpoint, result, phản hồi thoát, lưu trạng thái nhỏ và kết quả chờ |
| `games/` | Các module 2048, Sudoku và Tetris |
| `docs/` | Kiến trúc, quy ước, ví dụ bằng văn bản, phân công và kiểm thử |

Platform lưu lịch sử chơi cục bộ cho người chơi `guest`.

## Tài liệu nên đọc

1. [Kiến trúc](docs/01_KIEN_TRUC.md)
2. **[Cách tạo và thêm module game](docs/02_THEM_GAME.md)** — gửi tài liệu này cho ba thành viên.
3. [Hợp đồng game_core](docs/03_HOP_DONG_GAME_CORE.md)
4. [Phân công và tiêu chí bàn giao](docs/04_PHAN_CONG.md)
5. [Kiểm thử và xử lý lỗi](docs/05_KIEM_THU.md)
6. [Kết quả kiểm tra bản bàn giao](docs/06_KET_QUA_KIEM_TRA.md)

Nếu nâng từ bản có game giả lập trước đó, phần dọn dữ liệu chỉ loại bỏ checkpoint/kết quả đã được đánh dấu `demo=true`; kết quả thật không bị xóa. Mã ứng dụng giữ `com.example.arcade`, versionCode tăng lên 2.
