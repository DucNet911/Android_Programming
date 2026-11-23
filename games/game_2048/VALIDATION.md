# Kết quả kiểm tra bàn giao

Ngày: 05/11/2026. Nhánh gốc: `game_2048`, commit `36afb11`.

Lệnh tương đương đã chạy với JDK 17, Gradle 8.11.1, SDK 35:

```bash
./gradlew clean :app:assembleDebug testDebugUnitTest lintDebug -PexpectedGameId=game_2048
```

**BUILD SUCCESSFUL — 73 tests, 0 failures, 0 errors.**

| Bộ test | Số test |
| --- | ---: |
| `com.example.arcade.PlatformTest` | 8 |
| `com.example.arcade.catalog.GameRegistryTest` | 10 |
| `com.example.arcade.core.ContractTest` | 7 |
| `com.example.arcade.core.DefinitionTest` | 5 |
| `com.example.arcade.core.ModelsTest` | 6 |
| `com.example.arcade.core.StoreTest` | 6 |
| `com.team.game2048.Game2048ActivityTest` | 9 |
| `com.team.game2048.Game2048EngineTest` | 14 |
| `com.team.game2048.Game2048RenderTest` | 1 |
| `com.team.game2048.SnapshotCodecTest` | 7 |

APK debug của platform + game đã build thành công. Test registry kiểm tra game ID thực sự
xuất hiện trong Manifest đã gộp. Lint không có lỗi.

Các cảnh báo lint còn lại (không đổi dependency do dùng phiên bản nhóm đã quy định):

- `app` / `PluralsCandidate`: Formatting %d followed by words ("game"): This should probably be a plural rather than a string
- `app` / `PluralsCandidate`: Formatting %d followed by words ("điểm"): This should probably be a plural rather than a string
- `app` / `PluralsCandidate`: Formatting %d followed by words ("giây"): This should probably be a plural rather than a string
- `app` / `PluralsCandidate`: Formatting %d followed by words ("điểm"): This should probably be a plural rather than a string
- `app` / `DataExtractionRules`: The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute `android:dataExtractionRules` specifying an `@xml` resource which configures cloud backups and device transfers on Android 12 and higher.
- `app` / `Overdraw`: Possible overdraw: Root element paints background `@color/platform_background` with a theme that also paints a background (inferred theme is `@style/Theme.Arcade`)
- `app` / `Overdraw`: Possible overdraw: Root element paints background `@color/platform_background` with a theme that also paints a background (inferred theme is `@style/Theme.Arcade`)
- `app` / `SetTextI18n`: Do not concatenate text displayed with `setText`. Use resource string with placeholders.
- `app` / `SetTextI18n`: Do not concatenate text displayed with `setText`. Use resource string with placeholders.
- `app` / `SetTextI18n`: Do not concatenate text displayed with `setText`. Use resource string with placeholders.
- `games/game_2048` / `Overdraw`: Possible overdraw: Root element paints background `#FAF8F3` with a theme that also paints a background (inferred theme is `@style/G2048Theme`)

## Kiểm tra hình ảnh

Đã render giao diện Android bằng Robolectric native graphics ở 360 × 800
và xem ảnh để kiểm tra bố cục, tiếng Việt, bàn chơi và nút điều khiển.
Ảnh: [preview.png](docs/preview.png). Đây là ảnh render kiểm thử, không phải ảnh chụp từ điện thoại.

## Chưa kiểm tra trực tiếp

Chưa chạy trên điện thoại hoặc Android emulator. Cần kiểm tra thực tế độ mượt
60/120 Hz, cảm giác vuốt, TalkBack, chế độ cỡ chữ lớn và các kích thước thiết bị.
Các kiểm thử Activity là Robolectric, không thay thế kiểm thử thiết bị.

Chỉ thêm `games/game_2048/`; không sửa platform hoặc core. Không commit/push lên GitHub.
