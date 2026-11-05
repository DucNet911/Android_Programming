# Hướng dẫn cho người tự viết game

**Các đoạn dưới đây là ví dụ tài liệu, không phải module có sẵn trong app.** Thay tên `my_game`, package, tài nguyên và Activity bằng game của bạn. Sau khi thêm một module hợp lệ, chỉ cần Sync/Build; không sửa code platform.

## 1. Cấu trúc phải tạo

| Đường dẫn tính từ project | Nội dung do thành viên tạo |
|---|---|
| `games/game_my_game/build.gradle` | Android Library |
| `games/game_my_game/src/main/AndroidManifest.xml` | Activity và metadata đăng ký |
| `games/game_my_game/src/main/java/com/team/mygame/MyGamePlugin.java` | Implement GamePlugin |
| `games/game_my_game/src/main/java/com/team/mygame/MyGameActivity.java` | Màn hình chơi và vòng đời |
| `games/game_my_game/src/main/java/com/team/mygame/engine/` | Luật chơi Java |
| `games/game_my_game/src/main/java/com/team/mygame/data/` | Encode/decode trạng thái riêng |
| `games/game_my_game/src/main/res/layout/mygame_activity.xml` | UI XML |
| `games/game_my_game/src/main/res/values/strings.xml` | Tên game, mô tả, tên chế độ |
| `games/game_my_game/src/test/java/...` | Test luật chơi của thành viên |

Thư mục module phải là `game_` + chữ thường/số/gạch dưới; không dùng `game_core`. ID runtime không cần giống tên thư mục nhưng phải duy nhất, tối đa 64 ký tự, dạng `[a-z0-9][a-z0-9_]*`.

## 2. build.gradle của module

```groovy
plugins { id 'com.android.library' }
android {
    namespace 'com.team.mygame'
    compileSdk 35
    defaultConfig { minSdk 23 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
dependencies {
    implementation project(':game_core')
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.activity:activity:1.10.1'
    implementation 'androidx.core:core:1.15.0'
    testImplementation 'junit:junit:4.13.2'
}
```

Không đặt applicationId; không khai báo phiên bản AGP riêng; không tự thêm repositories trong module. Root đã quản lý các phần đó.

## 3. Metadata do game cung cấp

Tạo các string resource `mygame_name`, `mygame_description`, `mygame_mode_default` trong module. Ví dụ MyGamePlugin:

```java
package com.team.mygame;

import android.content.Context;
import android.graphics.Color;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.example.arcade.core.GamePlugin;
import java.util.Collections;

public final class MyGamePlugin implements GamePlugin {
    public MyGamePlugin() {}

    @Override
    public int getContractVersion() {
        return GameContract.VERSION;
    }

    @Override
    public GameDefinition getDefinition(Context context) {
        return new GameDefinition(
            "my_game",
            context.getString(R.string.mygame_name),
            context.getString(R.string.mygame_description),
            "MG",                       // Biểu tượng chữ trên card
            Color.rgb(185, 164, 255),    // Giá trị màu, KHÔNG phải resource ID
            MyGameActivity.class,
            GameDefinition.Ranking.HIGH_SCORE,
            Collections.singletonList(new GameDifficulty(
                "default", context.getString(R.string.mygame_mode_default)))
        );
    }
}
```

Có nhiều độ khó thì dùng `Arrays.asList(...)`; độ khó đầu tiên là mặc định. Mỗi ID độ khó phải duy nhất. Game không có độ khó vẫn khai báo một chế độ `default`.

Ranking chọn HIGH_SCORE, LOWEST_WIN_TIME (chỉ tính ván WON), hoặc NONE. Tên/mô tả/symbol không rỗng và tối đa 128 ký tự.

## 4. Đăng ký trong Manifest CỦA GAME

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="com.team.mygame.MyGameActivity"
            android:exported="false" />
        <meta-data
            android:name="com.example.arcade.GAME_PLUGIN.my_game"
            android:value="com.team.mygame.MyGamePlugin" />
    </application>
</manifest>
```

Hậu tố metadata **my_game phải bằng ID trong GameDefinition**. Đặt metadata trực tiếp dưới application, không trong activity. Không thêm LAUNCHER; không thay theme/label của application. Có thể đặt theme riêng cho Activity nếu cần. Game chạy cùng process app; không đặt android:process riêng.

Constructor plugin phải public, không tham số. Class plugin và Activity phải nằm trong APK. Activity phải enabled và exported=false. Core đã có consumer rule giữ class plugin khi dùng R8 sau này.

## 5. MyGameActivity cần làm gì?

Khi được mở:

```java
GameLaunchRequest request = GameContract.readRequest(getIntent());
```

- Kiểm tra gameId bằng `my_game`.
- Kiểm tra độ khó có trong definition; null dùng mặc định.
- NEW_GAME: game tạo UUID; khởi tạo engine và checkpoint với `demo=false`.
- RESUME: đọc đúng playerId/sessionId, khôi phục snapshot; thiếu ván phải báo SESSION_NOT_FOUND.
- Hoạt động trong app dùng AppCompatActivity nếu cần Activity Result/back/insets của AndroidX.

Tạo checkpoint (biến lấy từ engine/phiên chơi của bạn):

```java
GameSession session = new GameSession(
    "my_game", request.playerId, sessionId, difficultyId,
    activeDurationMs, snapshotJson, false
);
GameSessionStore store = new GameSessionStore(this);
store.save(session);
```

Lưu rồi quay về:

```java
store.save(session);
setResult(RESULT_OK,
    GameContract.responseIntent(GameExitResponse.saved(session)));
finish();
```

Kết thúc thật:

```java
GameResult result = new GameResult(
    "my_game", request.playerId, sessionId, difficultyId,
    GameOutcome.WON, scoreOrNull,
    activeDurationMs, System.currentTimeMillis(), false
);
store.finish(result); // Cần có checkpoint cùng danh tính trước đó.
setResult(RESULT_OK,
    GameContract.responseIntent(GameExitResponse.finished(result)));
finish();
```

Các đoạn này dùng biến do game tự quản lý; không phải Activity hoàn chỉnh để chạy game. Xử lý lỗi lưu trước khi báo thành công. Dừng loop/đồng hồ ở nền; không ghi lại checkpoint sau khi đã hoàn tất. Xem hợp đồng và checklist ở tài liệu 03/05.

## 6. Đưa vào platform và kiểm tra

1. Copy module vào `games/game_my_game/` hoặc commit module đó vào repository chung.
2. Gradle Sync; app tự thêm dependency và gộp Manifest.
3. Build/run app. Card phải xuất hiện với đúng tên từ GameDefinition.
4. Chạy:

```bash
./gradlew :app:assembleDebug testDebugUnitTest lintDebug -PexpectedGameId=my_game
```

Tham số expectedGameId khiến test kiểm tra đúng ID đã được khám phá từ Manifest, tránh build thành công nhưng game không xuất hiện.

Không sửa MainActivity, GameAdapter, GameRegistry hoặc game_core để thêm một game tuân thủ contract. Nếu cần tính năng mới như multiplayer/draw, bàn với người phụ trách core để mở rộng hợp đồng trước.
