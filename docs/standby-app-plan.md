# StandBy for Android — 実装計画

> 作成日: 2026-11-06  
> 状態: 計画（未実装）  
> 対象: Xiaomi 15 / Android 17（API 37）専用設計  
> 開発検証: まず Pixel 7a（Android 17 / API 37、ADB接続中）、後日 Xiaomi 15  
> パッケージ: `jp.yukirawa.standby`  
> 表示名: `StandBy`（変更可）  
> UI言語: 日本語  
> リポジトリ: `Android_StandBy`（GitHubでソース・Release APKを公開）

---

## 1. 概要

充電中（有線・無線・ドック）かつ横向きのときに、iPhone の StandBy に近い雰囲気の全画面スタンバイ画面を自動表示する Android ネイティブアプリを作る。

- 黒背景・大きな時計・ウィジェットカードによる OLED 向けの常時表示
- 時計コレクション、天気、音楽、バッテリー表示
- プリセット + 組み合わせによるカスタマイズ（時計・レイアウト・アクセント色・ウィジェット）
- 焼き付き防止（ピクセルシフト）、減光、赤色ナイトモード
- Xiaomi HyperOS の自動起動・バッテリー制限を考慮したオンボーディングと診断画面
- GitHub Actions によるタグ付けリリース

### このアプリで実現しないこと

- **画面オフのまま描画する本物の AOD は実現しない。** 一般アプリはディスプレイ消灯中に描画できないため、充電中は画面を点灯したまま低輝度で表示する「AOD風スタンバイ」とする。
- システムのロック画面そのものの置き換えはしない。`showWhenLocked` でロック画面上に自前の Activity を表示する。
- Google Play 公開は想定しない。個人利用 + GitHub 配布を前提に、必要な権限を積極的に使う。
- v1 ではカレンダー、フォトフレーム、ドラッグ&ドロップの自由配置は入れない。

---

## 2. 確定した要件

| 項目 | 決定 |
|---|---|
| 対象端末 | Xiaomi 15（Android 17 / API 37）専用 |
| 最低対応 | Android 17（API 37）のみ。`minSdk = 37` |
| 配布 | GitHub でソース公開 + Release APK。個人利用が中心 |
| v1 の要素 | 時計コレクション、天気、音楽プレイヤー、バッテリー/充電表示 |
| カスタマイズ | プリセット + 時計・レイアウト・色・ウィジェットの組み合わせ |
| 画面 | 充電中は常時点灯。焼き付き防止、復帰トリガーあり |
| 自動消灯 | 入れない（常時点灯）。減光のみ |
| ナイトモード | 手動切替の赤色表示 |
| デザイン | iPhone StandBy 風 + 独自アレンジ |
| 天気 | 現在地を自動取得。失敗時は東京にフォールバック |
| 音楽 | 通知アクセスを許可し、MediaSession を表示・操作 |
| テスト | Pixel 7a で開発 → Xiaomi 15 で最終確認 |
| リリース | GitHub Actions で APK をビルドし Release に添付 |

---

## 3. 実現方式と制約

### 3.1 全体方式

1. **常駐フォアグラウンドサービス**（`specialUse`）が充電・画面・向きを監視する。
2. 充電中 + 横向き + 表示条件を満たしたら、**`StandByActivity` を起動**する。
3. Activity はロック画面上に表示し、画面を点灯し、システムバーを隠して常時点灯する。
4. 充電解除・縦向き・ユーザー操作で終了する。
5. 設定で自動起動をオフにした場合だけサービスを停止する。

### 3.2 なぜこの方式か

- 画面消灯中に横向きを検知するには、充電中だけセンサーを動かす常駐サービスが必要。
- Android 10 以降はバックグラウンドからの Activity 起動が制限されるため、**「他のアプリの上に重ねて表示」権限（`SYSTEM_ALERT_WINDOW`）を主経路**にする。
- 権限がない場合のフォールバックとして、**全画面インテント通知**（`USE_FULL_SCREEN_INTENT`）と、**常駐通知のタップ**を用意する。
- Android 17 では Background Activity Launch（BAL）がさらに厳格化されているため、実機で主経路・フォールバックの両方を検証する。
- 画面消灯のまま描画する案（Overlay を画面オフ中も描く、DreamService を使う等）は、本物の AOD にはならない。必要なら将来の拡張とする。

### 3.3 Android 17 / HyperOS の注意点

- `ACTION_POWER_CONNECTED` は implicit broadcast の例外ではないため、**Manifest のレシーバーでは受け取れない**。サービス内の動的レシーバーで受ける。
- サービスは `BOOT_COMPLETED` と `MY_PACKAGE_REPLACED` から再起動する。`START_STICKY` も使う。
- Xiaomi HyperOS はバッテリー最適化や自動起動の設定でサービスを止めることがある。アプリ内で以下の設定導線を案内する。
  - 自動起動を許可
  - バッテリー使用を「制限なし」
  - 通知を許可
  - 必要なら「他のアプリの上に重ねて表示」を許可
- HyperOS の設定画面はバージョンで変わるため、`Intent` の直接起動は best-effort。失敗時はアプリ詳細設定またはシステム設定へフォールバックする。
- アプリを強制停止した場合は、再起動するまで自動起動しない。README と診断画面に明記する。

### 3.4 横向き検知の考え方

- 画面オンのときは `DisplayManager` の回転情報を優先する。
- 画面オフのときは加速度センサーで傾きを判定する。スタンドに横向きで置いた状態を `|roll| > 60°` 程度で判定し、1.5 秒のデバウンスとヒステリシスを入れる。
- 端末を完全に平置きした場合は加速度だけでは向きが曖昧になるため、**最後に取得した画面回転が横向きなら横向きとみなす**フォールバックを使う。
- 完全な平置きで縦向きのまま充電するケースは自動起動しない。必要になったら「平置きも許可」設定を将来追加する。

---

## 4. 機能仕様（v1）

### 4.1 自動起動

表示条件：

- 自動起動がオン
- 充電中（AC / USB / WIRELESS / DOCK のいずれか）
- 横向き（landscape left/right）
- 以下のいずれか
  - 画面オフ
  - ロック画面表示中
  - 「画面オンのときも起動」がオン
- ユーザーが明示的に閉じた直後ではない
- 通話中ではない（可能な範囲でガード）
- 端末が高温すぎない（将来の安全設定。v1 では診断表示のみ）

復帰・再武装：

- 充電を抜く → スタンバイ終了、抑制状態を解除
- 縦向きになる → 表示中なら終了。抑制中なら再武装
- 充電を再接続 → 再評価
- 画面オフ → 画面オン → 条件を満たせば再表示
- ユーザーが閉じた場合は、**縦向きになる / 充電を抜き差しする / 手動起動する**まで再表示しない
- 電源ボタンで画面を消した場合は、勝手に画面を起こさない。ユーザーが画面を点けたときに再評価する

### 4.2 スタンバイ画面

- 全画面・黒背景（OLED の黒を維持）
- ステータスバー・ナビゲーションバーを非表示（スワイプで一時表示）
- 横向き固定（`sensorLandscape`）
- 画面を常時点灯（`FLAG_KEEP_SCREEN_ON`）
- ロック画面上に表示（`setShowWhenLocked(true)`）し、ロック解除は要求しない
- 画面点灯（`setTurnScreenOn(true)`）
- 画面輝度はアプリ内設定（端末の明るさとは独立したウィンドウ輝度）
- タップで減光から復帰
- 長押しでクイックコントロール
- 下スワイプ / 戻るボタンで閉じる
- 終了時はフェードアウト

### 4.3 時計コレクション（5種類）

| 時計 | 内容 |
|---|---|
| Digital | 大きな数字。12/24時間、秒表示、日付表示のオン/オフ。薄いウェイトで iPhone 風 |
| Analog | Canvas 描画のミニマルなアナログ時計。時針・分針・秒針、日付窓 |
| World | 現在時刻 + 最大3都市の世界時計。都市は設定で選択（東京/ニューヨーク/ロンドン等） |
| Solar | 時間帯に応じた空のグラデーション + 太陽/月。天気が取れれば日の出・日の入りを反映 |
| Flip | パタパタ時計風。分が変わるときだけ軽いアニメーション |

- 秒表示は Digital / Analog / Flip で対応。秒なしの場合は毎分頭に更新。
- 日付は日本語表記（例: `11月6日 (金)`）。
- フォントは端末の `sans-serif-thin` / `sans-serif-light` を使う。SF Pro はライセンス上同梱しない。

### 4.4 ウィジェット（3種類）

| ウィジェット | 表示内容 | フォールバック |
|---|---|---|
| Weather | 現在地の気温、天気、体感温度、最高/最低、3時間予報、更新時刻 | 位置情報なし → 東京。通信失敗 → キャッシュ、なければプレースホルダー |
| Music | アルバムアート、曲名、アーティスト、再生/停止、前/次、進捗 | 通知アクセスなし → 設定導線。セッションなし → 「音楽を再生」ボタン |
| Battery | 残量、充電種別（有線/無線/ドック）、充電状態、温度、取得できれば概算W | 取得できない項目は非表示。残量と充電状態は必須 |

- 天気データ: Open-Meteo（APIキー不要）。現在地は概略位置（`ACCESS_COARSE_LOCATION`）。取得失敗時は東京（35.6762, 139.6503）。
- 天気の更新: 画面表示時 + 表示中30分ごと。直近の成功データを JSON キャッシュに保存。
- 音楽: `NotificationListenerService` + `MediaSessionManager`。通知本文は保存しない。メディアのメタデータと再生状態だけを使う。
- バッテリー: `ACTION_BATTERY_CHANGED`（sticky）+ `BatteryManager`。`BATTERY_PROPERTY_CURRENT_NOW` と電圧から概算Wを出すが、端末非対応時は表示しない。

### 4.5 レイアウト（3種類）

| レイアウト | 構成 |
|---|---|
| Hero | 時計を中央に大きく、ウィジェットを下部に横並び |
| Split | 左に時計、右にウィジェット縦積み（iPhone StandBy 風） |
| Stack | 上に時計、下にウィジェット横並び。余白多めのミニマル構成 |

イメージ：

```text
Hero
┌──────────────────────────────────────┐
│                                      │
│               07:42                  │
│            11月6日 (金)               │
│                                      │
│   [ 天気 ]      [ 音楽 ]    [ 電池 ]  │
└──────────────────────────────────────┘

Split
┌──────────────────────┬───────────────┐
│                      │ [ 天気 ]       │
│        07:42         │ [ 音楽 ]       │
│      11/6 (金)       │ [ 電池 ]       │
└──────────────────────┴───────────────┘

Stack
┌──────────────────────────────────────┐
│  07:42                               │
│  11月6日 (金)                         │
│                                      │
│   [ 天気 ]      [ 音楽 ]    [ 電池 ]  │
└──────────────────────────────────────┘
```

### 4.6 プリセット（6種類）

| プリセット | 雰囲気 | 時計 | レイアウト | アクセント |
|---|---|---|---|---|
| Midnight | 黒・白・シルバー。標準 | Digital | Hero | White |
| Aurora | 寒色のグラデーション | Solar | Split | Cyan |
| Ember | 温かいアンバー | Analog | Stack | Amber |
| Mono | 白黒ミニマル。時計と日付だけ | Digital | Hero | White |
| Studio | 紫・マゼンタ。音楽向け | World | Split | Violet |
| Crimson | 赤いナイトモード | Digital | Hero | Red |

- プリセット選択時に時計・レイアウト・色・ウィジェット構成を一括適用する。
- 適用後に個別項目を変更できる。変更内容は保存される。
- プリセットに戻すボタンも用意する。

### 4.7 カスタマイズ項目

- プリセット
- 時計の種類（5）
- レイアウト（3）
- アクセント色（White / Cyan / Amber / Violet / Red / Green）
- フォントの太さ（Thin / Light / Regular）
- 12/24時間
- 秒表示
- 日付表示・日付フォーマット
- 世界時計の都市（最大3）
- ウィジェットの表示/非表示
- ウィジェットのスロット配置（左/中/右、各1種類、重複なし）
- 画面輝度
- 減光までの時間（30秒 / 1分 / 2分 / 5分 / なし）
- 減光時の明るさ（5〜50%）
- 焼き付き防止（オン/オフ、間隔 60/90/120秒）
- ナイトモード（赤）
- 自動起動のオン/オフ
- 画面オフ時に画面を起こすか
- 画面オンのときも起動するか

### 4.8 クイックコントロール

スタンバイ画面を長押しすると、画面下から以下の操作を出せる。

- 明るさスライダー
- ナイトモードの切り替え
- プリセットの切り替え（6種）
- 閉じる

設定アプリを開く操作は、ロック解除が必要な場合があるためクイックコントロールには置かない。戻るか下スワイプで閉じる。

### 4.9 手動起動

- アプリ内の「プレビュー」ボタン
- クイック設定タイル「スタンバイ」
- 常駐通知の「スタンバイを開く」
- プレビューでは充電・横向き条件を無視し、画面を確認できる。終了しても抑制状態は作らない。

### 4.10 設定・オンボーディング

初回起動時にステップ形式で案内する。

1. StandBy の説明と「本物の AOD ではない」注意
2. 通知の許可（Android 13+）
3. 他のアプリの上に重ねて表示（オーバーレイ）
4. バッテリー最適化の除外
5. Xiaomi 15 / HyperOS の場合: 自動起動・バッテリー制限なしの案内
6. 任意: 通知アクセス（音楽）、位置情報（天気）
7. プリセット選択とプレビュー

- 必須権限がなくてもプレビューは可能。自動起動のカードに「要設定」を出す。
- 権限状態は画面復帰ごとに再取得する。
- Xiaomi の自動起動はプログラムから確認できないため、ユーザーのチェックボックスで管理する。

### 4.11 ホーム画面

- アプリ名と状態（待機中 / 停止中 / 要設定）
- 現在のプリセットのミニプレビュー
- 「プレビュー」
- 「自動起動」トグル
- 「見た目」「ウィジェット」「画面」「起動条件」「権限」「診断」「このアプリについて」
- 権限不足時のセットアップカード
- Xiaomi 向け設定カード
- バージョン、GitHub リンク

### 4.12 診断画面

以下を表示する。

- サービス稼働状態、フォアグラウンド状態
- 自動起動設定
- 充電状態・充電種別
- 画面オン/オフ、キーガード状態
- 向き・角度・横向き判定
- オーバーレイ権限、通知権限、通知アクセス、位置情報、バッテリー最適化
- 全画面インテント権限
- 最終トリガー時刻と理由
- 直近のイベントログ（最大100件、コピー可能）
- 天気の最終更新、音楽セッション、バッテリー情報

通知本文や個人情報はログに残さない。

### 4.13 エッジケース

- 充電を抜いた → 即終了
- 縦向きにした → 即終了
- 画面を手動で消した → 勝手に起こさない
- 戻る/下スワイプで閉じた → 同じ向き・充電状態では再表示しない
- 再起動 → 充電中ならサービス再開、条件を満たせば表示
- アプリ更新 → `MY_PACKAGE_REPLACED` でサービス再開
- 権限を取り消した → 自動起動せず、診断に表示
- ネットワークなし → キャッシュ天気、なければプレースホルダー
- 位置情報拒否 → 東京の天気
- 音楽セッションなし → プレースホルダー
- 通知アクセス拒否 → 音楽カードに設定ボタン
- 通話中 → 自動起動しない
- 強制停止 → 再起動まで自動起動しない（README に明記）

---

## 5. デザイン仕様

### 5.1 基本

- 背景: `#000000`（純黒）。OLED の消灯ピクセルを活かす
- カード: `#0B0B0B` + 白8%の1dpボーダー + 角丸20dp
- テキスト: 白85〜100%。補助テキストは白55%
- アクセント: プリセット色。面で塗らず、文字・アイコン・細い線に使う
- 影・ブラーは使わない（OLED とパフォーマンスのため）
- アニメーションは短く、控えめ（150〜300ms）。常時アニメーションは避ける

### 5.2 ナイトモード（赤）

- 背景: `#000000`
- 主色: `#FF453A` 系
- 補助: `#8E1B14`
- テキスト: `#FF6B60` / `#B33A31`
- アルバムアート・天気アイコンは赤系にティント
- 赤い半透明オーバーレイではなく、**最初から赤いカラートークンで描画**する。黒を黒のまま保つ

### 5.3 タイポグラフィ

- 時計: `FontFamily.SansSerif` + `FontWeight.Thin`（100）または `Light`（300）
- 数字はタブラー数字（`tnum`）で桁揺れを防ぐ
- 日付: `Light`、16〜22sp
- ウィジェット見出し: `Medium`、12〜14sp
- 本文: `Regular`、14〜18sp
- 画面サイズに応じて `BoxWithConstraints` で時計サイズを決める。端末のフォントスケールでレイアウトが壊れないよう、スタンバイ画面内は `Density` を考慮してクランプする

### 5.4 余白・レスポンシブ

- 外周: 24〜40dp（画面サイズに応じて）
- カード間: 12〜16dp
- パンチホール・カットアウトを避けるため、`WindowInsets.safeDrawing` を基本余白に使う
- Xiaomi 15 / Pixel 7a の横向き（約2.2:1）を基準にし、極端に横長でも崩れないよう `min()` でサイズを決める

### 5.5 焼き付き防止・減光

- コンテンツ全体を 60〜120秒ごとに 2〜4px の範囲で移動する
- 移動は 3×3 のオフセットパターンを循環し、1.5秒かけて滑らかに動かす
- 端に切れないよう、レイアウトに常に 8dp 以上の逃げを作る
- 無操作 60秒（設定可）でウィンドウ輝度を 20%（設定可）に下げる
- タップで通常輝度に戻す
- 画面は消さない（自動消灯なし）
- 長時間の常時点灯で焼き付きを完全に防ぐことはできない。README とオンボーディングに注意書きを入れる

### 5.6 システム UI

- ステータスバー・ナビゲーションバーを非表示
- `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` で必要なときだけ表示
- 画面の向きは `sensorLandscape`
- ロック画面上に表示するが、ロック解除はしない
- 画面には天気・音楽・バッテリー以外の個人情報を出さない

---

## 6. 技術設計

### 6.1 アーキテクチャ

- 単一 `app` モジュール
- Kotlin + Jetpack Compose
- 手動 DI（`AppContainer`）で依存をまとめる。Hilt は使わない
- MVVM + `StateFlow`
- 設定は DataStore（JSON）、天気キャッシュはファイル、音楽・バッテリーはメモリ内リポジトリ
- `TriggerEngine` は Android 非依存の純粋 Kotlin にして単体テスト可能にする

```text
StandByService
  ├─ ChargingMonitor ──┐
  ├─ ScreenStateMonitor ├─→ TriggerEngine ─→ LaunchStrategy ─→ StandByActivity
  ├─ OrientationMonitor ┘         │
  └─ ConfigRepository            └─ StandBySession（状態/イベント）
                                      ↑
StandByActivity ── StandByViewModel ──┤
  ├─ WeatherRepository                 │
  ├─ MusicRepository                   │
  └─ BatteryRepository ────────────────┘
```

### 6.2 トリガー状態機械

状態：

- `Disabled`: 自動起動オフ
- `Idle`: サービス稼働中、充電なし
- `Armed`: 充電中。横向き・画面状態を待っている
- `Visible`: スタンバイ表示中
- `Suppressed(reason)`: ユーザー操作などで再表示を抑制中

主な遷移：

| 現在 | イベント | 次 | 動作 |
|---|---|---|---|
| Idle | 充電開始 + 横向き + 表示条件OK | Visible | Activity 起動 |
| Idle | 充電開始 + 縦向き | Armed | 待機 |
| Armed | 横向きになる + 表示条件OK | Visible | Activity 起動 |
| Armed | 画面オン + ロック解除 + 設定オフ | Armed | 常駐通知のみ |
| Visible | 充電解除 | Idle | Activity 終了、抑制解除 |
| Visible | 縦向き | Armed | Activity 終了 |
| Visible | 戻る/下スワイプ | Suppressed(user) | Activity 終了 |
| Visible | 電源ボタンで画面オフ | Suppressed(power) | 画面を起こさない |
| Suppressed(user) | 縦向きになる | Armed | 抑制解除 |
| Suppressed(user) | 充電再接続 | Armed | 抑制解除 |
| Suppressed(power) | 画面オン | Visible | 条件OKなら再表示 |
| 任意 | 手動プレビュー | Visible(preview) | 条件を無視して表示 |

- 横向き判定は 1.5 秒のデバウンス。境界で暴れないようヒステリシスを入れる。
- 起動試行後 3 秒以内に Activity から「表示開始」通知が来ない場合は、全画面インテント通知または常駐通知にフォールバックする。
- サービス再起動時は現在の充電・画面・向きを再評価して状態を復元する。

### 6.3 主要クラス

| 領域 | クラス | 役割 |
|---|---|---|
| Application | `StandByApplication` | `AppContainer` の生成 |
| DI | `AppContainer` | リポジトリ、モニター、設定の生成 |
| サービス | `StandByService` | FGS、レシーバー、トリガー実行、通知 |
| トリガー | `TriggerEngine` | 純粋な状態機械。イベント→アクション |
| トリガー | `ChargingMonitor` | 充電状態・種別の監視 |
| トリガー | `ScreenStateMonitor` | 画面オン/オフ、キーガード |
| トリガー | `OrientationMonitor` | 加速度・画面回転から横向き判定 |
| 起動 | `LaunchStrategy` | オーバーレイ / 全画面インテント / 通知フォールバック |
| 状態 | `StandBySession` | Activity とサービスの共有状態、イベントログ |
| 画面 | `StandByActivity` | 全画面表示、ロック画面表示、常時点灯 |
| 画面 | `StandByViewModel` | 設定・天気・音楽・バッテリー・時刻の合成 |
| 画面 | `StandByScreen` | レイアウト、時計、ウィジェット、ジェスチャー |
| 設定 | `MainActivity` / `SettingsScreen` | ホーム、設定、オンボーディング |
| 設定 | `ConfigRepository` | DataStore への JSON 保存、プリセット適用 |
| 天気 | `WeatherRepository` | 位置取得、Open-Meteo、キャッシュ |
| 音楽 | `MusicRepository` | MediaSession の監視・操作 |
| 音楽 | `MediaNotificationListener` | 通知アクセス用のダミーリスナー |
| バッテリー | `BatteryRepository` | 残量、充電種別、温度、概算W |
| 権限 | `PermissionController` | 権限状態の取得と設定画面起動 |
| Xiaomi | `XiaomiSettings` | 自動起動・バッテリー設定の best-effort 導線 |
| タイル | `StandByTileService` | クイック設定タイル |
| レシーバー | `BootReceiver` | 起動時のサービス再開 |
| レシーバー | `PackageReplacedReceiver` | 更新時のサービス再開 |
| 診断 | `DiagnosticsLog` | リングバッファのイベントログ |

### 6.4 権限と起動戦略

| 権限/設定 | 用途 | 必須度 |
|---|---|---|
| `POST_NOTIFICATIONS` | FGS 通知、フォールバック通知 | 必須に近い |
| `SYSTEM_ALERT_WINDOW` | バックグラウンドからの Activity 起動の主経路 | 必須（主経路） |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 常駐監視サービス | 必須 |
| `WAKE_LOCK` | 画面オフ中のセンサー監視、画面点灯 | 必須 |
| `RECEIVE_BOOT_COMPLETED` | 再起動後のサービス再開 | 必須 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Xiaomi 等でのサービス維持 | 強く推奨 |
| `USE_FULL_SCREEN_INTENT` | オーバーレイ不可時のフォールバック | 推奨 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 天気取得 | 任意 |
| `ACCESS_COARSE_LOCATION` | 現在地の天気 | 任意 |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 音楽の表示・操作 | 任意 |
| `FOREGROUND_SERVICE_SPECIAL_USE` の property | 用途説明 | 必須 |

起動戦略：

1. `Settings.canDrawOverlays(context)` が true → `startActivity`
2. false かつ `NotificationManager.canUseFullScreenIntent()` が true → 全画面インテント通知
3. それ以外 → 高重要度の通知 + 常駐通知のアクション

- 常駐通知は低重要度・サイレント・ongoing。内容は「充電するとスタンバイを表示します」。
- 全画面インテント用の通知チャンネルは別途高重要度で作成する。
- 起動失敗を検知するため、`StandBySession` に `launchRequestedAt` を持ち、Activity の `onResume` で `started` を記録する。

### 6.5 データ設計

設定 JSON の例：

```json
{
  "version": 1,
  "presetId": "midnight",
  "layout": "HERO",
  "clockFace": "DIGITAL",
  "accent": "WHITE",
  "fontWeight": "THIN",
  "use24Hour": true,
  "showSeconds": false,
  "showDate": true,
  "datePattern": "M月d日 (E)",
  "worldCities": ["Asia/Tokyo", "America/New_York", "Europe/London"],
  "widgetSlots": ["WEATHER", "MUSIC", "BATTERY"],
  "nightMode": false,
  "brightness": 0.4,
  "dimDelaySeconds": 60,
  "dimBrightness": 0.2,
  "burnInShift": true,
  "burnInIntervalSeconds": 90,
  "autoLaunch": true,
  "wakeOnCharge": true,
  "launchWhenUnlocked": false,
  "weatherEnabled": true,
  "musicEnabled": true,
  "batteryEnabled": true
}
```

- `version` でマイグレーションする。
- 設定変更は `StateFlow` でサービスと Activity に即時反映する。
- 天気キャッシュは `filesDir/weather_cache.json` に保存（設定とは分離）。
- 通知内容は保存しない。音楽のメタデータもメモリ内のみ。

### 6.6 天気

- API: `https://api.open-meteo.com/v1/forecast`
- パラメータ例:
  - `current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m`
  - `hourly=temperature_2m,weather_code`
  - `daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset`
  - `timezone=auto`
- 位置: `LocationManager` の `getLastKnownLocation` を優先。なければネットワークプロバイダで単発取得。タイムアウト 10 秒。
- 概略位置で十分。バックグラウンド位置情報は取得しない。
- 通信は OkHttp。JSON は kotlinx.serialization。
- WMO weather code を日本語ラベルとアイコンにマップ。
- 失敗時はキャッシュ → 東京の順でフォールバック。画面に「東京の天気」と明示する。

### 6.7 音楽

- `MediaNotificationListener` は通知アクセスのためだけに存在し、通知内容は読まない・保存しない。
- `MediaSessionManager.getActiveSessions(ComponentName(context, MediaNotificationListener::class.java))` で取得。
- `MediaController.Callback` でメタデータ・再生状態・アクションを監視。
- 再生/停止/前/次は `TransportControls`。
- アルバムアートは `MediaMetadata.METADATA_KEY_ALBUM_ART` の Bitmap を優先。URI の場合は Coil で読み込む。
- セッションなし時は `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` か `CATEGORY_APP_MUSIC` で音楽アプリを開く。見つからなければ設定画面へ。

### 6.8 バッテリー

- `ACTION_BATTERY_CHANGED` の sticky Intent から初期値を取得。
- `EXTRA_PLUGGED`: AC / USB / WIRELESS / DOCK を日本語表示。
- `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` と `EXTRA_VOLTAGE` から概算Wを計算。符号・未対応値（`Long.MIN_VALUE` 等）をガード。
- 温度は `EXTRA_TEMPERATURE / 10.0`。
- 表示中は 30 秒ごとに更新。充電状態の変化は即時反映。

### 6.9 時刻・タイマー

- 秒表示あり: 1秒ごと。なし: 次の分頭に合わせて更新。
- 世界時計は `ZoneId` で計算。
- 減光・ピクセルシフト・天気更新は `LaunchedEffect` / `while (isActive)` で管理し、Activity が見えないときは停止。
- 画面消灯中にサービスが時計を更新する必要はない。Activity 表示中だけ動かす。

### 6.10 スレッド・ライフサイクル

- サービスはメインスレッドで状態管理。センサーコールバックはメイン。
- ネットワーク・ファイル IO は `Dispatchers.IO`。
- Activity は `repeatOnLifecycle(STARTED)` でフローを収集。
- ウェイクロックは「充電中 + 画面オフ + 自動起動オン」のときだけ取得し、画面オン・充電解除・サービス停止で解放する。タイムアウト付きで取得し、必要なら更新する。
- サービス停止時は必ず `stopForeground` / ウェイクロック解放 / センサー解除。

---

## 7. ビルド・依存

### 7.1 ツールチェーン

| 項目 | バージョン | 理由 |
|---|---|---|
| Gradle | 9.5.0 | AGP 9.3.1 の最小要件。既存の wrapper dist を使用 |
| AGP | 9.3.1 | API 37 対応。Compose 1.12 の最小 AGP 9.1.1 を満たす。Kotlin 2.4.21 との互換範囲 |
| Kotlin | 2.4.21 | KGP 互換表で AGP 9.3.1 / Gradle 9.5.0 に対応。AGP の built-in Kotlin を buildscript classpath で上書き |
| Compose Compiler | 2.4.21 | Kotlin と同一バージョン |
| Compose BOM | 2026.09.00 | Compose 1.12.1 系 |
| compileSdk | 37 | Android 17 |
| minSdk | 37 | Xiaomi 15 / Android 17 専用 |
| targetSdk | 37 | Android 17 の挙動に合わせる |
| Build Tools | 36.0.0 | AGP 9.3 の既定 |
| JDK | 21 | 環境にインストール済み。AGP の最小は 17 |

> AGP 9.3.1 は built-in Kotlin を 2.2.10 で同梱する。Kotlin 2.4.21 を使うため、ルートの `buildscript` に `org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21` を追加し、`org.jetbrains.kotlin.android` プラグインは適用しない。ビルドが通らない場合は AGP 同梱の Kotlin 2.2.10 + Compose Compiler 2.2.10 にフォールバックする。

### 7.2 主な依存

- `androidx.core:core-ktx:1.19.1`
- `androidx.activity:activity-compose:1.13.0`
- `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`
- `androidx.navigation:navigation-compose:2.10.2`
- `androidx.datastore:datastore-preferences:1.3.0`
- Compose BOM `2026.09.00`（ui / foundation / material3 / animation）
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0`
- `com.squareup.okhttp3:okhttp:5.5.1`
- `io.coil-kt.coil3:coil-compose:3.6.3`
- `androidx.core:core-splashscreen:1.2.0`
- テスト: JUnit 4、AndroidX Test、Compose UI Test
- 追加の大規模アイコン集は入れず、必要なアイコンだけ vector drawable を用意

### 7.3 リポジトリ構成

```text
Android_StandBy/
├─ .github/workflows/ci.yml
├─ .github/workflows/release.yml
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ java/jp/yukirawa/standby/
│     │  │  ├─ StandByApplication.kt
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ di/AppContainer.kt
│     │  │  ├─ data/config/...
│     │  │  ├─ data/weather/...
│     │  │  ├─ data/music/...
│     │  │  ├─ data/battery/...
│     │  │  ├─ service/...
│     │  │  ├─ receiver/...
│     │  │  ├─ standby/...
│     │  │  ├─ settings/...
│     │  │  ├─ permissions/...
│     │  │  ├─ tile/...
│     │  │  └─ ui/theme/...
│     │  └─ res/
│     │     ├─ drawable/...
│     │     ├─ mipmap-anydpi-v26/...
│     │     ├─ values/strings.xml
│     │     └─ xml/...
│     ├─ test/...
│     └─ androidTest/...
├─ gradle/libs.versions.toml
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew / gradlew.bat / gradle/wrapper/
├─ README.md
├─ PRIVACY.md
├─ CHANGELOG.md
├─ LICENSE
└─ docs/screenshots/
```

### 7.4 Gradle 初期設定

- `android.useAndroidX=true`
- `android.nonTransitiveRClass=true`
- `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`
- `android.defaults.buildfeatures.buildconfig=true`（診断用に `BuildConfig.DEBUG` を使う場合）
- `local.properties` は Git 管理外。CI は `ANDROID_HOME` を使う
- `versionCode` / `versionName` は `-P` で上書き可能。デフォルトは `1` / `0.1.0`
- Release は R8（minify + resource shrink）を有効化。署名設定は `keystore.properties` があれば読み、なければ debug 署名でビルド（CI の release は Secrets 必須）

### 7.5 GitHub Actions

`ci.yml`（push / pull_request）:

1. checkout
2. Temurin JDK 21
3. Gradle setup（cache）
4. Android SDK platform 37 / build-tools 36.0.0 を用意
5. `./gradlew lintDebug testDebugUnitTest assembleDebug`
6. debug APK を artifact にアップロード

`release.yml`（`v*` タグ）:

1. 上記と同じセットアップ
2. Secrets から keystore を復元
3. `./gradlew assembleRelease -PversionName=<tag> -PversionCode=<run number>`
4. APK と SHA256 を GitHub Release に添付
5. リリースノートは `CHANGELOG.md` から生成

必要な Secrets：

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

キーストアはリポジトリにコミットしない。初回リリース前に生成し、バックアップを取る。

---

## 8. テスト計画

### 8.1 単体テスト（JVM）

- `TriggerEngine` の全遷移
  - 充電開始/解除、横向き/縦向き、画面オン/オフ、ロック、抑制、再武装
  - デバウンス、ヒステリシス
- 向き計算（roll/pitch の境界、平置きフォールバック）
- 設定 JSON のシリアライズ / デシリアライズ / バージョン移行
- プリセット適用ロジック
- 天気 JSON パース、WMO code → 日本語ラベル
- バッテリーの概算W計算（未対応値、符号、異常値）
- 時刻フォーマット（12/24時間、世界時計、日付）
- ウィジェットスロットの重複防止

### 8.2 実機テスト（Pixel 7a / API 37）

| # | 内容 | 期待 |
|---|---|---|
| 1 | 初回起動 → オンボーディング | 権限ステップとプレビューが動く |
| 2 | 有線充電 + 横向き + 画面オフ | 2秒以内に表示 |
| 3 | 充電種別を wireless に変更（adb シミュレーション） | 同様に表示 |
| 4 | 縦向きにする | 終了 |
| 5 | 充電を抜く | 終了 |
| 6 | 戻るで閉じる → 同じ状態で待つ | すぐには再表示しない |
| 7 | 縦向き → 横向きに戻す | 再表示 |
| 8 | 電源ボタンで画面オフ | 勝手に画面が点かない |
| 9 | 画面オン + ロック解除中 | デフォルトでは自動表示しない |
| 10 | QS タイル / 通知 / プレビュー | 手動表示できる |
| 11 | ロック画面 | ロック解除なしで表示 |
| 12 | 天気: 位置情報あり / なし / オフライン | 現在地 / 東京 / キャッシュ |
| 13 | 音楽: Spotify 等で再生 / 停止 / セッションなし | 表示・操作・プレースホルダー |
| 14 | 通知アクセスなし | 設定導線が出る |
| 15 | ナイトモード | 赤色になり、黒は黒のまま |
| 16 | 減光・タップ復帰 | 輝度が下がり、タップで戻る |
| 17 | ピクセルシフト | 一定間隔で数px移動する |
| 18 | 再起動（充電中） | サービス再開、条件次第で表示 |
| 19 | アプリ更新 | サービス再開 |
| 20 | 権限取り消し | 自動起動せず、診断に表示 |

### 8.3 実機テスト（Xiaomi 15 / HyperOS、後日）

- 自動起動の許可 → サービスが落ちにくいこと
- バッテリー「制限なし」→ 一晩充電して翌朝も動くこと
- ロック画面上の表示
- 有線・無線充電の両方
- 通知アクセス、位置情報の権限導線
- HyperOS の設定画面へのディープリンク（失敗時のフォールバック含む）
- 画面輝度・常時点灯・焼き付き防止の実機確認
- 高温時の挙動（充電中の温度上昇）

### 8.4 ADB での確認例

```bash
# 充電シミュレーション
adb shell dumpsys battery unplug
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set wireless 1

# 画面オン/オフ
adb shell input keyevent 26

# 位置情報・通知アクセス（開発時）
adb shell pm grant jp.yukirawa.standby android.permission.ACCESS_COARSE_LOCATION
adb shell cmd notification allow_listener \
  jp.yukirawa.standby/.data.music.MediaNotificationListener

# バッテリー最適化の除外（開発時）
adb shell dumpsys deviceidle whitelist +jp.yukirawa.standby

# ログ
adb logcat -s StandBy:V
```

> adb の充電シミュレーションは実際の無線充電の挙動を完全には再現しない。最終確認は Xiaomi 15 の実機で行う。

### 8.5 パフォーマンス・品質

- `dumpsys gfxinfo` でスタンバイ画面のフレーム時間を確認
- `dumpsys power` でウェイクロックのリークがないこと
- 30分連続表示後のバッテリー温度・メモリを確認
- 画面消灯中にセンサー以外の処理が動いていないこと
- 端末回転時に Activity が二重起動しないこと
- サービス再起動時に通知が重複しないこと

---

## 9. 受け入れ条件

- Pixel 7a（Android 17）でビルド・インストールできる
- 充電中 + 横向き + 画面オフで、2秒以内にスタンバイが表示される
- 有線・無線（シミュレーション含む）の両方でトリガーする
- ロック画面上に表示され、ロック解除を要求しない
- 縦向き・充電解除で終了する
- 閉じた直後に勝手に再表示しない。再武装条件が機能する
- 時計5種、レイアウト3種、プリセット6種、ウィジェット3種が動く
- 天気・音楽・バッテリーが表示され、取得失敗時も破綻しない
- ナイトモード、減光、ピクセルシフトが動く
- 設定が再起動後も保持される
- オンボーディングと診断画面が機能する
- GitHub Actions の CI が通り、タグで Release APK が作れる
- Xiaomi 15 実機での最終確認項目（HyperOS の自動起動・バッテリー・無線充電）を完了する

---

## 10. マイルストーン

| マイルストーン | 内容 | 完了条件 |
|---|---|---|
| M0: 足場 | Gradle / AGP / Compose / テーマ / CI の雛形 | `assembleDebug` が通り、Pixel 7a にインストールできる |
| M1: トリガー | FGS、充電・画面・向き監視、TriggerEngine、Activity 起動、権限オンボーディング | 充電 + 横向きで表示・終了できる |
| M2: 見た目 | 時計5種、レイアウト3種、プリセット6種、DataStore、設定画面 | 設定変更が即時反映され、再起動後も保持 |
| M3: ウィジェット | 天気、音楽、バッテリー、フォールバック | 3種が表示・操作でき、失敗時も崩れない |
| M4: 仕上げ | ナイトモード、減光、ピクセルシフト、クイックコントロール、診断、QS タイル | 常時点灯系の機能が実機で動く |
| M5: リリース | 単体テスト、README、スクリーンショット、CI/Release、Xiaomi 15 検証 | タグから APK が Release され、Xiaomi で確認完了 |

目安：M0〜M4 で 6〜8 作業日、M5 で 1〜2 作業日。Xiaomi 15 の接続時期に依存する。

---

## 11. リスクと対策

| リスク | 影響 | 対策 |
|---|---|---|
| Android 17 の BAL 制限で Activity が起動しない | 自動表示できない | オーバーレイ権限を主経路にし、全画面インテント + 通知フォールバックを実装。起動監視で 3 秒後に切り替える |
| HyperOS が FGS を停止する | 充電しても反応しない | 自動起動・バッテリー制限なしのオンボーディング、診断画面、`START_STICKY`、README のトラブルシュート |
| 本物の AOD を期待される | 不満 | オンボーディングと README で「画面オフの AOD ではない」と明示 |
| 画面オフ中のセンサーが止まる | 横向きを検知できない | 充電中のみ部分ウェイクロック + 加速度センサー。失敗時は最後の画面回転でフォールバック。実機検証 |
| 平置き時の向きが曖昧 | 誤判定 | 最後の画面回転を使う。完全な平置きは対象外と明記 |
| OLED 焼き付き | 画面に残像 | 純黒背景、ピクセルシフト、減光、赤ナイトモード。完全保証はしないと明記 |
| 通知アクセスがプライバシー的に重い | 敬遠される | 通知内容は読まない・保存しない。音楽メタデータのみ利用。README とオンボーディングで説明 |
| 天気 API の失敗 | 表示が空 | キャッシュ、東京フォールバック、プレースホルダー |
| 充電中の発熱 | 端末温度上昇 | 低輝度、黒背景、温度表示。将来は高温時の自動減光/終了を追加 |
| リリース署名鍵の紛失 | 更新できない | 鍵を生成したら安全にバックアップ。Secrets と README に手順を記載 |
| AGP / Kotlin / Compose のバージョン不整合 | ビルド不能 | 上記の組み合わせを固定。M0 で最初にビルド検証。失敗時は AGP 同梱 Kotlin 2.2.10 にフォールバック |
| HyperOS の設定画面パスが変わる | 案内が機能しない | 直接 Intent は best-effort。アプリ詳細設定・システム設定へのフォールバックを用意 |

---

## 12. 未決事項・前提

- **ライセンス**: とりあえず MIT を想定。公開前に確定する。
- **表示名**: `StandBy` を想定。商標等が気になる場合は `NightStand` などに変更可能。
- **GitHub リポジトリ名**: 作業ディレクトリ名に合わせて `Android_StandBy` を想定。
- **Xiaomi 15 の実機接続**: 現時点では Pixel 7a のみ ADB 接続中。Xiaomi 15 での最終確認は M5 で行う。
- **署名鍵**: 初回リリース前に生成し、GitHub Secrets に登録する。鍵のバックアップはユーザー側で管理する。
- **アプリアイコン**: 時計/月/スタンドをモチーフにした adaptive icon を自作する。外部画像は使わない。
- **v1 に入れないもの**: カレンダー、フォトフレーム、自由配置、プリセットの横スワイプ切替、自動消灯、夜間スケジュール、DreamService 連携。必要なら v1.1 以降で追加する。
- **Xiaomi 15 の HyperOS バージョン**: 実機接続時に設定導線を確認し、必要なら文言を調整する。

---

## 13. 参考

- Android 17 の動作変更（全アプリ）: https://developer.android.com/about/versions/17/behavior-changes-all
- Android 17 の動作変更（targetSdk 37）: https://developer.android.com/about/versions/17/behavior-changes-17
- フォアグラウンドサービスの種類: https://developer.android.com/about/versions/14/changes/fgs-types-required
- バックグラウンドからの Activity 起動: https://developer.android.com/guide/components/activities/background-starts
- 全画面インテント: https://developer.android.com/develop/ui/views/notifications/build-notification#full-screen-intent
- Compose BOM マッピング: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- AGP 9.3 リリースノート: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Open-Meteo API: https://open-meteo.com/en/docs
- Kotlin Gradle プラグイン互換性: https://kotlinlang.org/docs/gradle-configure-project.html
