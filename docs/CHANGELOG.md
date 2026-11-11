# Changelog

このプロジェクトの主な変更点を記録します。形式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に近い形で、バージョンは [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]

## [0.2.0] - 2026-11-11

### Added

- Android の戻るジェスチャー（予測型戻る）対応
  - マニフェストに `enableOnBackInvokedCallback` を明示
  - カスタマイズ / 診断画面を戻るでホームへ戻れるように変更
  - オンボーディングは戻るで前のステップへ（最初のページではスキップ）
  - ホーム以外の画面では、戻るジェスチャーの進行に合わせて画面がスライドする
- プレビューカードの時計をライブ表示（秒/分の境界に合わせて更新）

### Changed

- 時計の更新で画面全体が毎秒再コンポーズされないように変更
  - 時刻を `State<Instant>` として渡し、表示するコンポーザブルだけを無効化
  - Solar の背景は分単位、時計は秒単位（アナログ・秒表示時）で更新
  - 画面が STARTED でない間（バックグラウンド・プレビューが画面外）は更新を停止
- 設定の永続値を読み終えてから画面とサービスを開始し、オンボーディングのちらつきと
  サービス起動/停止の往復を解消
- 戻る操作まわりの UI 調整
  - スタンバイ画面のヒントに「戻るで閉じる」を追記
  - ヘッダーの戻るボタンを 44dp のタップ領域とアクセシビリティラベル付きに変更
- スタンバイ画面の明るさ制御を Compose 側に一本化（Activity の重複コレクタを削除）
- 未使用の依存関係（`navigation-compose` / `core-splashscreen` / `lifecycle-viewmodel-compose`）と
  不要な SDK バージョン分岐を削除
- アダプティブアイコンの定義を `mipmap-anydpi-v26` から `mipmap` へ移動（minSdk 37 では
  バージョン修飾子は不要）

### Fixed

- 天気キャッシュの読み書きをメインスレッドから IO ディスパッチャへ移動
- 診断画面のクラッシュログ読み込みを IO ディスパッチャへ移動
- 終了時の `overridePendingTransition`（Android 14 以降は無視される）を
  `overrideActivityTransition` に置き換え
- `StandByConfig` / `WeatherState` / `MusicState` を `@Immutable` にして Compose のスキップを有効化
- 時刻フォーマッタ・アナログ時計の `Paint`・アルバムアートの `ImageBitmap` をキャッシュ
- Flip クロックとバッテリー表示の数値フォーマットをロケール非依存に
- スタンバイ表示開始時の天気・音楽の二重リフレッシュを削除

## [0.1.0] - 2026-11-08

### Added

- 充電中 + 横向き + 画面オフ / ロック中で表示するスタンバイ画面
- 時計 5 種（Digital / Analog / World / Solar / Flip）
- レイアウト 3 種（Hero / Split / Stack）
- 天気・音楽・バッテリーウィジェット（3 スロット）
- 6 プリセット、6 アクセントカラー、日付フォーマット、フォントウェイト
- 減光、明るさ調整、赤色ナイトモード、ピクセルシフト
- 長押しクイックコントロール、タップ復帰、下スワイプ / 戻るで終了
- 常駐フォアグラウンドサービス（specialUse）と自動起動トリガー
- オーバーレイ権限 → 全画面インテント通知 → 常駐通知のフォールバック
- QS タイル、BOOT_COMPLETED / MY_PACKAGE_REPLACED からの復帰
- 権限オンボーディング、診断画面
- HyperOS 導線（自起動・バッテリー制限・ロック画面/バックグラウンドポップアップ権限エディタ）
- 診断画面で前回のクラッシュログを確認（ADB 不要）
- Material 3 Expressive のテーマ、Emphasized typography、Wavy progress
- `TriggerEngine` と設定モデルの単体テスト（23 件）

### Changed

- 自動起動をロック画面専用に変更（画面オン・ロック解除中は起動しない）
- 表示中にロックを解除するとスタンバイ画面を自動で閉じる
- 「ロック解除中も表示」設定を削除
- 監視サービスに `stopWithTask="false"` を設定し、タスク終了後も監視を継続

### Fixed

- 加速度センサーの全サンプルで通知を更新していたため発生していた著しいラグ
  - センサー頻度を `SENSOR_DELAY_NORMAL` に変更
  - デバウンス後の向き判定が変わったときだけ状態を更新
  - 常駐通知は表示状態が変わったときだけ更新
  - 初回のみ `startForeground()`、以降は `notify()`
- カスタマイズ画面を開くと落ちる問題
  - 横並びの `ChipsRow` が `verticalScroll` を使っていたのを `horizontalScroll` に修正
- スタンバイ画面のドラッグ中に毎フレーム全体再コンポーズしていた問題
  - 減光タイマーのリセットを `Channel(CONFLATED)` に変更
- スライダー操作中に DataStore の書き戻しが新しい値を上書きする競合
  - 設定の読み込みを初回のみに変更
- スライダー操作ごとに DataStore へ書き込んでいた問題
  - 220 ms のデバウンス保存に変更
- 診断ログ追加のたびにホーム全体が再コンポーズされていた問題
  - 診断画面内でのみ収集するように変更

[Unreleased]: https://github.com/yukirawa/Android_StandBy/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/yukirawa/Android_StandBy/releases/tag/v0.2.0
[0.1.0]: https://github.com/yukirawa/Android_StandBy/releases/tag/v0.1.0
