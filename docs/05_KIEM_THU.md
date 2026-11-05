# Kiểm thử và xử lý lỗi

## Platform mặc định

```bash
./gradlew :app:assembleDebug testDebugUnitTest lintDebug
```

Kỳ vọng: chỉ hai module app/game_core, không khai báo plugin game trong Manifest APK; trang chủ 0 game; không có nút vào game; lịch sử rỗng trên cài mới; tìm kiếm/yêu thích vẫn an toàn.

## Sau khi nhận game

```bash
./gradlew :app:assembleDebug testDebugUnitTest lintDebug -PexpectedGameId=<id_game>
```

Test kiểm tra các khai báo Manifest của module thật, kể cả version và Activity. Các class Probe/TestPlugin trong `src/test` chỉ là test doubles, không có trong APK. Test giả lập danh sách rỗng vẫn chạy độc lập khi sau này thêm module.

| Ca kiểm tra thiết bị | Kết quả |
|---|---|
| Chưa có module | 0 game, trạng thái rỗng, không crash |
| Thêm một module đúng cấu trúc rồi Sync/Build | Card xuất hiện mà không sửa app/core |
| Thêm nhiều module | Đủ số card, tìm kiếm/yêu thích/lịch sử phân biệt ID |
| Lưu → thoát → tiếp tục | Cùng session ID, giữ dữ liệu |
| Đưa app về nền, xoay màn hình | Không tạo ván mới hoặc cộng thời gian nền |
| Kết thúc rồi mở lại app | Một bản ghi duy nhất |
| Chỉ thoát để chơi tiếp | Không tính thành ván đã hoàn thành |
| Xóa module khỏi source rồi Sync/Build | Card biến mất; lịch sử thật cũ không bị xóa |

## Không thấy game

1. Thư mục là games/game_<ten>, có build.gradle hoặc build.gradle.kts ở ngay gốc module.
2. Plugin Gradle là com.android.library, có namespace riêng, implementation project(':game_core').
3. Metadata nằm dưới application, name bắt đầu đúng `com.example.arcade.GAME_PLUGIN.`, hậu tố bằng ID GameDefinition.
4. Class plugin public, constructor public rỗng, implements GamePlugin, version bằng GameContract.VERSION.
5. Activity được khai báo đầy đủ, enabled, exported=false, cùng process app.
6. Xem Logcat tag **ArcadeRegistry** và tab Merged Manifest của app. Registry ghi lỗi thay vì làm crash trang chủ.
7. Sync và build lại; app đã cài trước đó không tự có source mới.

Tên game ID trùng bị từ chối; metadata name trùng còn có thể gây lỗi manifest merge ngay khi build. Không tự thêm tools:replace để che xung đột ID.

## Lỗi công cụ

- JDK/Gradle không tương thích: chọn Gradle JDK 17; giữ wrapper 8.11.1 với AGP 8.9.2.
- SDK location not found: cài SDK và mở Android Studio để tạo local.properties riêng; không commit đường dẫn máy.
- Thiếu Platform35/Build Tools35: cài bằng SDK Manager.
- Plugin/dependency không tải được: kiểm tra mạng/proxy hoặc Gradle Offline.
- Lỗi duplicate dex/resource sau khi thêm hoặc xóa module: chạy `./gradlew clean` rồi build lại để bỏ đầu ra cũ.
- Permission denied với gradlew: chmod +x gradlew hoặc dùng bash ./gradlew.
- INSTALL_FAILED_UPDATE_INCOMPATIBLE: APK cũ và mới có debug key khác; build bằng cùng keystore hoặc gỡ bản debug cũ rồi cài lại (gỡ app xóa dữ liệu của bản đó).

Báo cáo tests: <module>/build/reports/tests/testDebugUnitTest/index.html.
Báo cáo lint: <module>/build/reports/lint-results-debug.html.
