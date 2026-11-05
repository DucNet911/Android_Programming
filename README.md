# Arcade Platform — chỉ platform, 0 game

Ứng dụng Android Java + XML có giao diện nền tối, trang chủ, tìm kiếm, yêu thích, lịch sử SQLite, lưu/tiếp tục và kết quả chung. **Bản bàn giao không chứa game, module mẫu hoặc card game giữ chỗ.**

Project mặc định chỉ build hai module: `app` và `game_core`. Thư mục `games/` ban đầu chỉ có tài liệu. Trang chủ hiển thị **0 game** và “Chưa có trò chơi nào”. Tất cả/Lịch sử/Yêu thích vẫn mở được khi danh sách rỗng.

## Chạy platform

1. Giải nén ZIP; mở thư mục `ArcadePlatform` chứa `settings.gradle` bằng Android Studio.
2. Chọn **Gradle JDK 17**; cài Android SDK Platform 35 và Build Tools 35.0.0.
3. Sync Gradle, chọn module `app`, chạy trên Android 6.0+ (API 23+).
4. Hoặc cài APK platform rỗng được đóng gói trong `APK/` cạnh thư mục source.

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

## Các phần giữ lại

| Thành phần | Vai trò |
|---|---|
| `app` | Trang chủ, danh sách rỗng/có game, tìm kiếm, yêu thích, điều hướng, lịch sử và thành tích |
| `game_core` | API đăng ký game, request, checkpoint, result, phản hồi thoát, lưu trạng thái nhỏ và kết quả chờ |
| `games/` | Chỗ nhận source module do các thành viên tự viết; ban đầu không có module |
| `docs/` | Kiến trúc, quy ước, ví dụ bằng văn bản, phân công và kiểm thử |

Chạy offline với người chơi `guest`. Chưa có backend, đăng nhập thật, bảng xếp hạng online hoặc cơ chế tải game động.

## Tài liệu nên đọc

1. [Kiến trúc](docs/01_KIEN_TRUC.md)
2. **[Cách tạo và thêm module game](docs/02_THEM_GAME.md)** — gửi tài liệu này cho ba thành viên.
3. [Hợp đồng game_core](docs/03_HOP_DONG_GAME_CORE.md)
4. [Phân công và tiêu chí bàn giao](docs/04_PHAN_CONG.md)
5. [Kiểm thử và xử lý lỗi](docs/05_KIEM_THU.md)
6. [Kết quả kiểm tra bản bàn giao](docs/06_KET_QUA_KIEM_TRA.md)

Nếu nâng từ bản có game giả lập trước đó, phần dọn dữ liệu chỉ loại bỏ checkpoint/kết quả đã được đánh dấu `demo=true`; kết quả thật không bị xóa. Mã ứng dụng giữ `com.example.arcade`, versionCode tăng lên 2.
