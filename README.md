# Tinque

[MusicBeatSP](https://github.com/Kdroidwin/MusicBeatSP) をベースにしたAndroid用ローカル音楽プレイヤーです。  
左右独立・一括対応のイコライザーやチャンネルスワップ、フォルダビュー機能の搭載、およびプレイヤー開閉アニメーションの変更を行っています。

[![対応OS](https://img.shields.io/badge/対応OS-Android%208.0%2B-3DDC84?style=flat-square)](https://developer.android.com)
[![パッケージID](https://img.shields.io/badge/App%20ID-com.music.tinque-blue?style=flat-square)](#エディション一覧)
[![Fork元](https://img.shields.io/badge/Forked%20from-MusicBeatSP-gray?style=flat-square)](https://github.com/Kdroidwin/MusicBeatSP)
[![ライセンス](https://img.shields.io/badge/License-GPL%20v3-green?style=flat-square)](LICENSE)

<br/>

[**主な特徴・変更点**](#主な特徴変更点) · [**エディション一覧**](#エディション一覧) · [**ビルド手順**](#ビルド手順) · [**クレジット**](#クレジット)

---

## 主な特徴・変更点

- **パラメトリックイコライザー（一括・左右独立対応）**
  - 左右両チャンネルへ一括して適用する標準設定に加え、左右それぞれに個別の周波数・ゲイン・Q値を適用できる左右独立設定（L/R個別PEQ）に対応。
- **チャンネルスワップ機能**
  - イヤホンや音源に合わせて、左右の音声出力をワンタップで反転可能。
- **アニメーションの変更**
  - ボトムバーと全画面プレイヤー間の開閉アニメーションの挙動を変更（全画面カバーアート表示時の遷移挙動を含む）。
  - 再生バー上の歌詞および全画面歌詞画面における切り替えアニメーションの変更。
- **ボトムバー展開固定オプション**
  - 下部のミニプレイヤーを展開した状態で画面上に固定表示する設定を追加。
- **フォルダビュー機能**
  - 端末内のフォルダ階層をそのまま参照して楽曲を選択・再生可能。
- **独立したパッケージID**
  - パッケージIDを `com.music.tinque` に変更。MusicBeatSP や BitChord と同時に同一端末へインストール可能です。

---

## エディション一覧

| エディション | パッケージID | 概要 |
| :--- | :--- | :--- |
| **標準版** | `com.music.tinque` | 通常のAndroid端末向け（推奨） |
| **QQ Music 互換版** | `com.tencent.qqmusic` | OnePlus / OPPO端末でシステムDolby Atmos等を有効化するためのパッケージ |
| **KuGou 互換版** | `com.kugou.android` | OnePlus / OPPO向けの代替互換パッケージ |
| **オフライン版** | `com.music.tinque.nointernet` | インターネット接続権限を含まない版 |

---

## ビルド手順

### 動作環境
- JDK 17
- Android SDK Platform 37
- CMake 3.22.1 以上

### ビルドコマンド
```bash
git clone https://github.com/nanbeimerco/Tinque.git
cd Tinque

# 標準デバッグAPKのビルド
./gradlew assembleStandardDebug

# 標準リリースAPKのビルド
./gradlew assembleStandardRelease
```

ビルド完了後、APKは `app/build/outputs/apk/standard/<debug|release>/` に出力されます。

---

## クレジット

- **[MusicBeatSP](https://github.com/Kdroidwin/MusicBeatSP)** (by Kdroidwin) — ベースとなったプロジェクト
- **[BitChord](https://github.com/kushagrasinghx/BitChord)** (by Kushagra Singh) — UIおよび再生基盤
- **[InnerTune](https://github.com/z-huang/InnerTune)** (by Zion Huang) — 先行プレイヤープロジェクト
- **[TagLib](https://github.com/kyant0/taglib)** — メタデータ処理エンジン
- **[Haze](https://github.com/chrisbanes/haze)** — すりガラス効果ライブラリ

---

## ライセンス

本プロジェクトは GNU General Public License v3.0 (GPL-3.0) のもとで公開されています。
詳細は [LICENSE](LICENSE) をご確認ください。
