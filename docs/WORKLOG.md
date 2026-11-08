# StandBy 実装作業ログ

計画書: `standby-app-plan.md`  
作業対象: Pixel 7a（ADB接続）→ 後日 Xiaomi 15  
記録形式: 時刻 / できたこと / 詰まったこと / 気分（`+` はポジティブ、`-` はネガティブ、`!` は要注意）

---

## 2026-11-06 夜〜11-07 午前: M0 足場

- 環境確認
  - `adb devices` で Pixel 7a（`32271JEHN06738`, Android 17 / API 37）を確認。
  - Android SDK は `~/Android/Sdk`、platform 37 / build-tools 36.0.0 が既にあった。
  - Gradle 9.5.0 の distribution が `~/.gradle/wrapper/dists` にあったので、wrapper 生成前はそれを直接使用。
  - JDK 21。
- Gradle プロジェクト作成
  - AGP 9.3.1 + Kotlin 2.4.21 + Compose BOM 2026.09.00。
  - `android.defaults.buildfeatures.buildconfig` は AGP 9 で削除済みと怒られたので削除。
  - Compose 有効化には Compose Compiler Gradle Plugin が必須。`org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.21` を buildscript に入れて解決。
  - `local.properties` に `sdk.dir=/home/yukirawa/Android/Sdk` を書いてビルド成功。
- UI方針
  - ユーザー指示により Jetpack Compose のみ（XMLレイアウトなし）。
  - ユーザー指示により **Material 3 Expressive** を採用。
  - BOM 2026.09.00 の material3 は 1.4.0 で、`MaterialExpressiveTheme` が internal だった。`1.5.0-rc01` を明示指定すると public な Expressive API が使えたのでこちらを採用。
  - `MaterialExpressiveTheme` + `MotionScheme.expressive()` + `ToggleButton` / `LinearWavyProgressIndicator` / Emphasized typography を使う方針。
- Pixel 7a に skeleton APK をインストール・起動確認。
- 気分
  - `+` 最初のビルドが15分かかって「え、なんで？」となったが、2回目以降は数分に短縮。
  - `-` adb が一度 `no devices` になった。USB自体は見えていたので `adb kill-server` → `start-server` で復帰。以後安定。
  - `+` Material 3 Expressive の 1.5.0-rc01 が見つかってすんなり解決。
  - `!` ユーザーから「Material 3 Expressiveを使って」と複数回指示があった。最初のテーマ実装が通常の MaterialTheme だったので、Expressive 明示に修正した。

## 2026-11-07: M1〜M3 実装開始

- 設定モデル / プリセット / 時刻フォーマット
  - `StandByConfig`（JSON、DataStore保存）、6プリセット、5時計、3レイアウト、アクセント6色。
  - ウィジェットスロットは3枠・重複なしを正規化。
  - `TriggerEngine` は Android 非依存の純 Kotlin で実装（単体テスト予定）。
- モニター
  - `BatteryRepository`: 充電種別・残量・温度・概算W。
  - `ScreenStateMonitor`: 画面ON/OFF・キーガード。
  - `OrientationMonitor`: 加速度 + 画面回転、1.5秒デバウンス、ヒステリシス、平置きフォールバック。
- データ
  - Open-Meteo 天気（位置情報→東京フォールバック、JSONキャッシュ）。
  - MediaSession 音楽（通知内容は読まない）。
- サービス / 通知
  - `StandByService`（specialUse FGS）、`LaunchStrategy`（オーバーレイ→全画面インテント→通知）、`StandBySession`。
- 気分
  - `+` TriggerEngine を純 Kotlin に分離できたのは気持ちいい。テストが書きやすい。
  - `-` Android 17 の API 名（`ACTION_KEYGUARD_LOCKED` 等）が怪しく、コンパイル未確認のまま削った。早めにビルドしたい。
  - `!` `NotificationManager.canUseFullScreenIntent()` など API 37 前提の呼び出しが増えてきた。ビルドで一気に潰す。
  - `+` 作業ログを書く運用は振り返りに良さそう。

## 次の作業

- M1: StandByActivity / サービスをコンパイル・実機で起動確認。
- M2: StandBy画面（時計5種・レイアウト3種）と設定画面。
- M3: 天気・音楽・バッテリーウィジェット。
- M4: 減光・ピクセルシフト・クイックコントロール・診断・QSタイル。
- その後: 単体テスト、README、CI、Xiaomi 15 接続後に HyperOS 最適化。

## 2026-11-07: M1〜M4 画面実装と、実機で出たラグ・クラッシュの修正

- 実装
  - `StandByScreen.kt`: Hero / Split / Stack、3スロットのウィジェット、タップ復帰、長押しクイックコントロール、下スワイプ/戻るで終了、減光、ピクセルシフト、30分ごとの天気更新。
  - `MainActivity.kt`: ホーム / カスタマイズ / 診断 / オンボーディングを Material 3 Expressive で実装。Expressive の `ToggleButton`、`LinearWavyProgressIndicator`、Emphasized typography を使用。
  - QSタイル、BOOT_COMPLETED / MY_PACKAGE_REPLACED レシーバー、通知リスナー、specialUse フォアグラウンドサービスを Manifest に登録。
  - 通知アイコン・タイルアイコンを追加。
- 実機（Pixel 7a）でユーザーから報告された問題
  - `!` アプリ全体が「クソラグい」。
  - `!` 「カスタマイズ」を押すとアプリが落ちる。
  - `!` ADBでデバッグ中に system が応答不能になり、端末が再起動した。以後 ADB 操作は停止。
- 原因調査と修正
  - `+` ラグの主因: `OrientationMonitor` が加速度センサーの全サンプル（約60Hz）で `StateFlow` を更新し、`StandByService` がそのたびに `notify()` と `startForeground()` を呼んでいた。ログにも `Shedding notify ... rate limit exceeded` が大量に出ていた。
    - センサーは `SENSOR_DELAY_NORMAL` に変更。
    - 生の roll/pitch を毎回 emit せず、デバウンス後の「横向き判定」が変わったときだけ `StateFlow` を更新。
    - 常駐通知は表示状態（visible / autoLaunch）が変わったときだけ更新。
    - 初回だけ `startForeground()`、以降は `notify()` に限定。
  - `+` クラッシュの主因: 設定画面の `ChipsRow` が横並びなのに `verticalScroll` を使っていた。外側の `Column.verticalScroll` から無限高さ制約を受けて `IllegalStateException` になる典型的なパターン。`horizontalScroll` に修正。
  - `+` 設定スライダーが1ドラッグごとに DataStore へ書き込んでいたため、`ConfigRepository.update()` を即時反映＋220msデバウンス保存に変更。
  - `+` 診断ログの収集を `MainApp` 全体から診断画面内へ移動し、ログ追加のたびにホーム全体が再コンポーズされるのを回避。
  - `+` スタンバイ画面のドラッグ中、`interactionTick` という snapshot state を毎フレーム更新していたため画面全体が再コンポーズされていた。`Channel(CONFLATED)` で減光タイマーをリセットする方式に変更し、ドラッグ中の再コンポーズをゼロにした。
  - `+` `ConfigRepository` が DataStore を常時 collect していたため、デバウンス書き込みの完了後に古い設定がメモリ上の新しい値（スライダー操作中）を上書きする競合があった。初回読み込みのみ `first()` で反映する方式に変更。
- 単体テスト
  - `+` `TriggerEngineTest`（15件）と `StandByConfigTest`（8件）を追加。`org.json` はローカル単体テストでスタブになるため `testImplementation("org.json:json:20240303")` を追加。
  - `+` 23件すべて成功。`launch failure` はエンジン単体では再試行可能で、実際の15秒クールダウンは `StandByService` 側が持つことをテストで明確化。
- 気分
  - `-` 実機で「ラグい」「落ちる」と言われたのはかなり焦った。しかも端末の system が巻き込まれて再起動したので「やばい」。
  - `+` ログの `Shedding notify` とコードを見比べて、60Hz で通知を更新していた原因を特定できたのはすんなり行った。
  - `-` 設定画面のクラッシュは、ビルドが通ったことに安心してスクロール方向を確認していなかった。初歩的なミス。
  - `+` 単体テストはすんなり通った。テスト側の前提違いを実装バグと切り分けられたのは良かった。
  - `!` 今後は実機検証を最小限にし、ADB はユーザーの合図があるまで触らない。ビルドと静的確認を優先する。

## 2026-11-07: M5 リリース準備（実機を触らずに進められるところまで）

- ドキュメント
  - `+` `README.md`（機能・対応環境・インストール・権限・HyperOS・ビルド・トラブルシューティング）、`PRIVACY.md`、`CHANGELOG.md`、`LICENSE`（MIT）、`docs/screenshots/` を追加。
  - `+` README には「本物の AOD ではない」「強制停止後は再起動まで自動起動しない」を明記。
- ビルド / リリース
  - `+` Gradle Wrapper 9.5.0 を追加（`./gradlew` でビルド可能に）。
  - `+` `app/build.gradle.kts` に署名設定を追加。`keystore.properties` があれば release 鍵、なければ debug 鍵で署名。
  - `+` 使い捨てキーで `assembleRelease` を実行し、`apksigner verify` で署名を確認。R8 後の APK は約 3.0 MB。
  - `+` `.github/workflows/ci.yml`（push / PR: lint・単体テスト・debug APK artifact）と `release.yml`（`v*` タグ: Secrets から鍵を復元、release APK と SHA256 を GitHub Release に添付）を追加。
- Lint / テスト
  - `+` `lintDebug` が `NotificationManagerCompat.notify` の `MissingPermission` で失敗。`POST_NOTIFICATIONS` の明示チェックと `@SuppressLint` を追加して通過。
  - `+` `assembleDebug` / `testDebugUnitTest`（23件）/ `lintDebug` / `assembleRelease` すべて成功。
  - `!` CI の Android SDK パッケージ名は `platforms;android-37` ではなく `platforms;android-37.0` だった。実機の `sdkmanager --list_installed` を見て修正。
- クラッシュ調査の導線
  - `+` `StandByApplication` に未捕捉例外を `filesDir/last_crash.txt` へ保存するハンドラを追加。診断画面に「前回のクラッシュ」カードを追加し、ADB なしで原因を確認できるようにした。
- 気分
  - `+` 実機が使えない間にリリース周りを一気に整えられてすんなり進んだ。
  - `-` SDK のパッケージ名ゆれ（37 / 37.0）に気づかず、最初は CI が落ちる構成になっていた。危ない。
  - `!` ラグ・クラッシュ修正とリリース APK はまだ実機で確認できていない。端末が落ち着いたら最小限の確認だけ行う。

## 2026-11-07: Pixel 7a 実機テスト（修正版 APK）

- 実施
  - `+` `adb install -r app-debug.apk` → Success。起動後 `MainActivity` が前面、`last_crash.txt` なし、FATAL なし。
  - `+` ホーム画面の表示・スクロールOK。`設定` → `カスタマイズ` 遷移OK。`ChipsRow` クラッシュ再発なし。
  - `+` 明るさスライダー 55% → 89% に変更 → 戻る → 再表示で 89% を維持（DataStore 永続化OK）。テスト後に 55% へ戻す。
  - `+` `プレビュー` 起動OK。中央長押しでクイックコントロール表示、明るさ 56% → 41% → 57% に反映、シート外タップで閉じる、下スワイプでプレビュー終了してホームへ戻る。すべてクラッシュなし。
- 自動起動テスト
  - `!` 充電中（USB 100%）＋物理横向きで `KEYCODE_SLEEP` を送ったが、`StandByActivity` は起動せず `MainActivity` のまま。3秒後には `mWakefulness=Awake`。
  - `+` 原因候補を特定: `settings get global stay_on_while_plugged_in` が `7`、`mStayOn=true`。開発者オプションの「充電中は画面をスリープしない」が有効で、画面オフ条件が成立していなかった可能性が高い。
  - `!` 次回は「充電中はスリープしない」を一時的にオフにするか、電源ボタンで物理的に画面を消して再検証する。アプリ側のバグと決めつけない。
- 気分
  - `+` 前回報告の「ラグ」「カスタマイズで落ちる」は修正版で再現せず、スライダー永続化まで確認できて一安心。
  - `+` 長押しクイックコントロールと下スワイプ終了も実機で動いた。
  - `-` 自動起動だけ確認できず、しかも原因が端末の開発者設定だった可能性。早く決めつけなくてよかった。
  - `!` 外出で接続が切れるため、ここで一旦停止。次回は自動起動の条件を1つずつ切り分ける。

## 2026-11-07: Pixel 7a 実機テスト完了

- 気分
  - `+` 前回報告の「ラグ」「カスタマイズで落ちる」は修正版で再現せず、スライダー永続化まで確認できて一安心。
  - `+` 長押しクイックコントロールと下スワイプ終了も実機で動いた。
  - `-` 自動起動だけ確認できず、しかも原因が端末の開発者設定だった可能性。早く決めつけなくてよかった。
  - `!` 外出で接続が切れるため、ここで一旦停止。次回は自動起動の条件を1つずつ切り分ける。

## 次の作業

- 自動起動の再検証
  - `stay_on_while_plugged_in` を一時的に `0` にする、または開発者オプションの「充電中は画面をスリープしない」をオフにして、充電中＋横向き＋画面オフで `StandByActivity` が起動するか確認。
  - 画面オフ時に `OrientationMonitor` が加速度から横向きを拾えているか、アプリ内の診断ログで確認。
  - 縦向きに戻したとき／充電を外したときに終了するか確認。
- 実機で残っている確認
  - ホームのスクロールとスライダー操作のラグが再発しないか、しばらく使って確認。
  - プレビューのタップ復帰・下スワイプ終了は確認済み。必要なら減光・ピクセルシフトも実機で確認。
- Xiaomi 15 接続後に HyperOS の自起動・バッテリー制限・ワイヤレス充電・ロック画面表示・焼き付きを最終確認。

## 2026-11-08: ロック画面専用化と HyperOS 対応

- 自動起動の条件
  - `+` `TriggerEngine` から `launchWhenUnlocked` を削除し、「充電中 + 横向き + 画面オフ/ロック中」だけに限定した。
  - `+` 表示中にロックを解除したら `FINISH` を返すようにし、ホーム画面にスタンバイが残らないようにした。
  - `+` 表示開始時にも `evaluate()` を呼び、起動直後の状態ずれを防いだ。
  - `-` 最初は「表示中の解除」だけ直せば十分だと思っていたが、起動時評価も必要だった。実機で残る挙動を見て初めて気づいた。
- 画面オフからの復帰
  - `+` 画面オフ時はアクティビティを起動しておき、電源ボタンでロック画面を点けるとスタンバイが見える挙動で確定。ユーザー了承済み。
  - `!` 自動点灯は `PowerManager.wakeUp()` が公開 API でなく、全画面インテントでも端末側の制御で点かないことがあった。無理にウェイクロックを弄らず、電源ボタン前提にした。
- HyperOS
  - `+` `XiaomiSettings.openPermissionEditor()` を追加（`miui.intent.action.APP_PERM_EDITOR`）。ホーム・診断・オンボーディングから開けるようにした。
  - `+` 監視サービスに `android:stopWithTask="false"` を追加し、タスク swipe 後も監視を続けるようにした。
  - `+` README の HyperOS 手順を、自起動・バッテリー・権限エディタ・通知・全画面インテントまで具体化した。
  - `!` Xiaomi 15 は未接続のため、HyperOS 側はコードとドキュメントのみ。実機確認は次回。
- リポジトリ
  - `+` `docs/NEXT.md` に次回の Xiaomi 実機確認項目を整理。root は README のみにした。
  - `+` `release.yml` の CHANGELOG パスを `docs/CHANGELOG.md` に修正。
  - `!` コミットが遅れた。動く状態で早めに区切るべきだった。
- 気分
  - `+` ロック画面専用の挙動は実機で期待どおり。ホーム画面で起動しないことも確認できた。
  - `-` 画面が自動で点かない問題に時間を使った。端末ポリシーの壁だと分かってからは早かった。
  - `!` HyperOS は実機でしか分からない部分が残る。v0.1.0 は出しつつ、Xiaomi 15 で追検証する。

