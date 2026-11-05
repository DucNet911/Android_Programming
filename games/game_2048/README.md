# 2048 — module cho ArcadePlatform

Module Android Library viết bằng Java 17 + XML, dành cho nhánh `game_2048` của
`DucNet911/Android_Programming`. Game ID: `game_2048`; chế độ: `default`.

## Cài đặt

1. Chuyển repository của bạn sang nhánh `game_2048` và cập nhật nhánh.
2. Giải nén gói bàn giao tại thư mục gốc repository. Kết quả phải là
   `Android_Programming/games/game_2048/build.gradle`, không lồng thêm một thư mục `games`.
3. Mở thư mục gốc bằng Android Studio, chọn Gradle JDK 17, cài Android SDK Platform 35
   và Build Tools 35.0.0 nếu thiếu, rồi Sync Gradle.
4. Chọn cấu hình chạy **app**, không chạy riêng module thư viện. Mở thẻ **2048**.

Không sửa `app`, `game_core`, `settings.gradle` hay `build.gradle` gốc.
Platform tự tìm module, thêm dependency và đọc metadata trong Manifest.

### Kiểm tra trên máy bạn

Windows PowerShell (từ thư mục gốc repository):

```powershell
.\gradlew.bat :app:assembleDebug testDebugUnitTest lintDebug -PexpectedGameId=game_2048
```

macOS/Linux:

```bash
bash ./gradlew :app:assembleDebug testDebugUnitTest lintDebug -PexpectedGameId=game_2048
```

### Commit / push bằng Terminal của Android Studio

Chuyển nhánh trước khi sao chép module:

```bash
git switch game_2048
git pull --ff-only origin game_2048
```

Sau khi sao chép và kiểm tra:

```bash
git status
git add games/game_2048
git commit -m "Add Vietnamese 2048 game with animations and save support"
git push origin game_2048
```

Chỉ thêm source module. Không commit `local.properties`, `.gradle/`, `build/` hoặc SDK.

## Giao diện và luật chơi

- Giao diện tiếng Việt, nền kem, bàn 4 × 4, thẻ điểm / kỷ lục, bốn nút hướng.
- Vuốt trên bàn, dùng nút hướng hoặc phím mũi tên để chơi.
- Hai ô ban đầu; mỗi lượt hợp lệ thêm một ô 2 (90%) hoặc 4 (10%).
- Mỗi ô chỉ ghép một lần mỗi lượt; điểm cộng bằng giá trị ô vừa ghép.
- Ô trượt khoảng 150 ms, sau đó ô ghép nảy nhẹ và ô mới hiện dần trong khoảng 80 ms.
  Trong chuyển động, lượt nhập bổ sung được bỏ qua để tránh chồng trạng thái.
- Đạt 2048 lần đầu: pháo hoa ngắn, dòng chúc mừng; không âm thanh, popup hoặc khóa chơi.
  Cờ đạt mục tiêu được lưu; tiếp tục chơi vẫn trong cùng session.
- Không còn nước đi: hiển thị thông báo trên màn hình, lưu kết quả một lần.
- Ván mới có xác nhận khi đang chơi; ván cũ ghi WON nếu đã đạt 2048,
  nếu chưa thì ABANDONED. Game over trước mục tiêu ghi LOST.
- Quay lại / Lưu và thoát chỉ lưu ván đang chơi, không tạo lịch sử hoàn thành.
- Kỷ lục được tách theo playerId và cập nhật cả trong ván đang chơi.
- Bố cục cuộn được trên màn hình nhỏ/ngang; bàn giới hạn 520dp trên màn hình lớn.

## Cấu trúc

| File | Trách nhiệm |
| --- | --- |
| `Game2048Plugin.java` | Metadata, ranking HIGH_SCORE, Activity |
| `Game2048Activity.java` | Contract, session, vòng đời, thời gian, lưu/thoát |
| `engine/Game2048Engine.java` | Luật Java thuần, chuyển động từng ô, RNG có trạng thái |
| `data/SnapshotCodec.java` | JSON schema 1 và kiểm tra dữ liệu khi đọc |
| `ui/BoardView.java` | Vẽ Canvas, vuốt, trượt, ghép, pháo hoa |
| `res/layout/g2048_activity.xml` | Bố cục XML |
| `res/values/strings.xml` | Chuỗi tiếng Việt |
| `src/test/...` | Kiểm thử luật, JSON, Activity và tích hợp session |

Snapshot lưu 16 ô, điểm, cờ thắng và trạng thái RNG. Vì RNG được lưu, việc khôi phục
không thay đổi chuỗi ô mới. UUID được giữ khi tiếp tục hoặc tái tạo Activity.
Thời gian dùng `SystemClock.elapsedRealtime()`, loại trừ khi app ở nền hoặc mở hộp thoại.
Không ghi checkpoint sau khi hoàn tất; kết quả đi qua outbox `GameSessionStore.finish()`
để platform ghi lịch sử và chống trùng. Lỗi ghi dữ liệu không báo thành công;
game dừng nhận lượt và cung cấp nút thử lại.

## Checklist kiểm tra thiết bị

- [ ] Thẻ 2048 xuất hiện; giao diện tiếng Việt, không bị thanh hệ thống che.
- [ ] Vuốt / nút / phím điều khiển đúng; ô trượt và ghép mượt, không ghép hai lần một lượt.
- [ ] Về nền và trở lại: bàn giữ nguyên, thời gian nền không tính vào ván.
- [ ] Xoay màn hình: bàn, điểm và session giữ nguyên.
- [ ] Lưu và thoát → Tiếp tục: giữ bàn và điểm; chưa thêm lịch sử hoàn thành.
- [ ] Đạt 2048: pháo hoa một lần, vẫn chơi được; tiếp tục sau khi lưu không lặp pháo hoa.
- [ ] Hết nước đi: kết quả chính xác, không ghi hai lần khi thoát / xoay màn hình.
- [ ] Ván mới: xác nhận, ghi ván cũ và tạo UUID mới.
- [ ] Kiểm tra cỡ chữ lớn, màn hình nhỏ/ngang, TalkBack qua các nút hướng.

Các nút có nhãn trợ năng; bàn Canvas chưa cung cấp điều hướng TalkBack theo từng ô.
Xem `VALIDATION.md` để biết những kiểm tra đã thực sự chạy khi bàn giao.
