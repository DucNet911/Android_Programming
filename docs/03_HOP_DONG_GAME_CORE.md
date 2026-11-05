# Hợp đồng dùng chung v1

Game_core chỉ cung cấp API/metadata/truyền dữ liệu và tiện ích lưu trạng thái nhỏ. Không có bàn chơi hay tên game cố định.

## Đăng ký

`GamePlugin.getContractVersion()` phải bằng `GameContract.VERSION` (hiện là 1). `getDefinition(Context)` trả GameDefinition hợp lệ. Class public, constructor public không tham số; không gây side effect trong constructor/getDefinition.

## Yêu cầu mở — GameLaunchRequest

| Trường | Quy ước |
|---|---|
| gameId | ID duy nhất trùng definition và metadata |
| playerId | Bản đầu là guest; vẫn phân tách dữ liệu theo trường này |
| launchMode | NEW_GAME hoặc RESUME |
| sessionId | NEW_GAME null; RESUME bắt buộc có |
| difficultyId | Null dùng chế độ đầu tiên; giá trị không hỗ trợ phải báo lỗi |

Một ván mới có UUID mới do game tạo. Chơi tiếp, xoay màn hình hoặc Activity tái tạo phải giữ UUID cũ.

## Checkpoint — GameSession

Giữ gameId/playerId/sessionId/difficultyId, activeDurationMs, snapshot và demo. Game thật luôn truyền **demo=false**. `demo` chỉ được giữ để nhận diện dữ liệu từ bản cũ, không có module mẫu trong platform hiện tại.

Snapshot là JSON riêng do game định nghĩa; nên có trường schema. Ví dụ cần lưu đầy đủ bàn chơi, điểm, điều kiện thắng, seed hoặc hàng đợi nếu liên quan. Core không đọc các trường này. Không đưa View, Context, ảnh hoặc âm thanh vào snapshot.

Một checkpoint hoạt động cho mỗi cặp game/player. Không ghi đè session ID khác; người chơi phải tiếp tục hoặc chủ động bỏ ván cũ. GameSessionStore giới hạn snapshot 100.000 ký tự và dùng SharedPreferences commit để xác nhận ghi; dữ liệu lớn cần giải pháp file/DB riêng.

## Kết quả — GameResult

| Trường | Ý nghĩa |
|---|---|
| gameId/playerId/sessionId/difficultyId | Khớp checkpoint |
| outcome | WON, LOST, ABANDONED |
| score | Long nullable; null là không tính điểm, 0 là thực sự 0 điểm |
| activeDurationMs | Thời gian thực chơi, mili giây, >=0 |
| finishedAtEpochMs | System.currentTimeMillis() khi kết thúc, >0 |
| demo | false cho game do thành viên viết |

Đếm thời gian bằng SystemClock.elapsedRealtime(), loại thời gian ở nền/tạm dừng. Không trừ hai timestamp giờ hệ thống để đo thời lượng.

Giữ nguyên UUID khi gửi lại kết quả. Platform chống ghi trùng theo game/player/session. Không trả FINISHED khi chỉ lưu và thoát.

## Phản hồi — GameExitResponse

| reason | Dữ liệu | Platform xử lý |
|---|---|---|
| FINISHED | GameResult | Ghi lịch sử và cập nhật thành tích |
| SAVED_FOR_LATER | GameSession metadata | Hiện Tiếp tục; không ghi một ván hoàn thành |
| CANCELLED | Game/player | Không có ván mới; không ghi lịch sử |
| ERROR | GameErrorCode | Báo lỗi; ván hỏng có lựa chọn xóa riêng có xác nhận |

Dùng CANCELLED khi hủy trước khi tạo ván. Sau khi có ván, chọn lưu hoặc ABANDONED theo quyết định của người chơi.

Mã lỗi: INVALID_REQUEST, UNSUPPORTED_VERSION, SESSION_NOT_FOUND, SESSION_EXISTS, CORRUPT_SAVE, STORAGE_ERROR. Decoder từ chối version không hỗ trợ bằng exception; Activity có thể quy đổi thành error response thích hợp.

## Quy tắc theo ba game nhóm dự định làm

Bảng này chỉ hướng dẫn, không đăng ký hay đưa game vào app.

| Game dự định | Metadata do thành viên tự cung cấp | Kết quả |
|---|---|---|
| Tetris | Ranking HIGH_SCORE; chế độ tùy thiết kế | LOST khi game over; điểm thực |
| 2048 | Ranking HIGH_SCORE | WON khi đạt mục tiêu; nếu chơi tiếp phải giữ cờ đã đạt mục tiêu |
| Sudoku | Ranking LOWEST_WIN_TIME; các độ khó riêng | WON khi bảng đúng; score=null; thời gian theo độ khó |

Caro hoặc game khác có hòa/đa người chơi cần mở rộng enum/mô hình trước; không dùng LOST để thay cho hòa.
