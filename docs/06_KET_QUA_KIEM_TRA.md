# Kết quả kiểm tra bản platform không có game

Ngày 04/11/2026.

- Clean build APK debug: thành công.
- Source giao chỉ có module app và game_core; games/ chỉ có README.
- APK không có metadata đăng ký game; chỉ có Activity trang chủ và lịch sử.
- DEX không chứa class game cũ hoặc class plugin kiểm tra.
- Kiểm thử thêm module: đã thêm một Android Library tạm vào games/, build và xác nhận registry tự đọc đúng ID từ Manifest. Module kiểm tra đã được loại bỏ trước lần clean build cuối và không nằm trong gói bàn giao.

| Module | Tests đạt | Lint errors | Lint warnings |
|---|---:|---:|---:|
| app | 18 | 0 | 10 |
| game_core | 24 | 0 | 0 |

Tổng **42 kiểm thử đạt**, không bỏ qua test. Ngoài ra có một lần kiểm tra tích hợp module thật ở thời điểm build, đã đạt.

Các test kiểm tra danh sách rỗng, metadata plugin hợp lệ/không hợp lệ, xung đột ID, version, Activity nội bộ, tìm kiếm/yêu thích/mở game qua test doubles, request/result, lưu checkpoint/outbox, chống trùng SQLite và chỉ dọn dữ liệu demo cũ.

Test doubles đặt trong src/test; không có trong ứng dụng cài đặt. Các test đăng ký module thật vẫn hoạt động khi thành viên bổ sung game về sau.

## Cảnh báo lint

- DataExtractionRules: 1.
- Overdraw: 2.
- PluralsCandidate: 4.
- SetTextI18n: 3.

Lint không có Error/Fatal. Cảnh báo còn lại là gợi ý tài nguyên số nhiều, lớp nền UI, bản địa hóa chuỗi và cấu hình sao lưu; không che lỗi bằng baseline.

## Giới hạn xác minh

Kiểm thử JVM/Robolectric trên SDK mô phỏng 28 và kiểm tra cấu trúc APK. Chưa chạy trên thiết bị/emulator thật và chưa kiểm thử luật game vì bản này không chứa game. Nhóm cần thực hiện checklist thiết bị khi tích hợp game của mình.

Lệnh tái kiểm tra:

```bash
./gradlew clean :app:assembleDebug testDebugUnitTest lintDebug
```
