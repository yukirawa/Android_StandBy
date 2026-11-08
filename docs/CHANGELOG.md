# Changelog

このプロジェクトの主な変更点を記録します。形式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に近い形で、バージョンは [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]

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

[Unreleased]: https://github.com/yukirawa/Android_StandBy/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/yukirawa/Android_StandBy/releases/tag/v0.1.0
