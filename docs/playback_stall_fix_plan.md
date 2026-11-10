# Implementation plan：再生が 0.11秒周期で止まる問題（AudioTrack のライブロック）

元の報告書：`Downloads/playback_stall_investigation_report.md`
作成：2026-11-10 ／ 状態：計画のみ（コード未変更）

---

## 1. 報告書の検証結果

報告書の中心となる主張を、実際に使っているライブラリ（`media3-exoplayer-1.11.0.aar`）のバイトコードを `javap` で逆アセンブルして確認した。

| 報告書の主張 | 確認結果 |
|---|---|
| Media3 は 1.11.0 | ✅ `app/build.gradle.kts` の media3 系はすべて 1.11.0 |
| `MediaCodecAudioRenderer.isReady()` に 100ms の猶予がある | ✅ `hasPendingData()` が false でも、`hasBeenReady && isStarted && isSourceReady() && !hasReadStreamToEnd()` なら最初の 100ms は true を返す（定数 `100l`） |
| `AudioTrackPositionTracker` のスタック検知は Android 10 以下でしか働かない | ✅ `updateRawPlaybackHeadPosition` で `SDK_INT <= 29` かつ再生ヘッド 0 のときだけ `forceResetWorkaroundTimeMs` を立てる。`isStalled()` はこの値を見て 200ms で true を返すだけ |
| 推定再生位置は書き込み済みの長さでクランプされ、そのとき同期パラメータがリセットされる | ✅ `getCurrentPositionUs` 内で `sampleCountToDurationUs(writtenFrames)` と比較し、`resetSyncParams()` と `audioTimestampPoller.reset()` を呼ぶ分岐がある |
| シーク／曲変更で直る | ✅ シークは `onPositionReset` → `DefaultAudioSink.flush()` → AudioTrack を作り直す経路（報告書どおり） |

使っている端末は vivo V2337A（Android 17 / SDK 37）。Android 10 以下向けのスタック検知は一切働かない。

**結論：** 報告書のメカニズムは正しいと判断する。
- 根本原因は「外部出力（USB-DAC / Bluetooth）で AudioTrack の書き込みが進まなくなること」。
- それが Media3 の「READY ⇄ BUFFERING の往復」に化けている。
- Media3 には自動で抜け出す仕組みがない（Android 11 以降では動かない）。

### 補足：報告書と違う点・注意点
- 100ms の猶予は止まる原因ではない。往復の周期（約 0.13秒）を決めているだけ。猶予がなかったとしても、AudioTrack が詰まっていれば READY と BUFFERING を往復するか、BUFFERING のまま止まる。
  → **Media3 を 1.10 に戻しても根本解決にはならない可能性が高い**（周期が変わるだけかもしれない）。
- このアプリは独自のオーディオ処理チェーン（`buildAudioSink`：リサンプラ、PEQ、空間オーディオ、ReplayGain、無音スキップ、Sonic）を通している。処理が詰まって AudioTrack に書き込めなくなる可能性もゼロではないため、検証の段階でログを取って切り分ける（§4）。

---

## 2. 方針

**「詰まりを検知したら、今の位置へシークして AudioTrack を作り直す」ウォッチドッグを入れる**（報告書の対策案A）を主軸にする。

理由：
- ユーザーが手でシークしたときと同じ経路なので、確実に直り、副作用が少ない。
- Media3 の内部（final クラスや非公開フィールド）に手を入れずに済む。
- 原因が DAC 側でも Bluetooth 側でもアプリの処理チェーン側でも、同じ方法で抜け出せる。

---

## 3. 実装内容

### Step 1：検知（`PlaybackService`）
新しいクラス `playback/AudioStallWatchdog.kt` を作り、`PlaybackService` の `playbackListener` から呼ぶ。

**「詰まっている」と判断する条件（すべて満たすとき）：**
1. `playWhenReady == true` かつ `playbackSuppressionReason == NONE`（ユーザーは再生したがっている）
2. 直近 **1.5秒** の間に `STATE_READY → STATE_BUFFERING` が **4回以上** 起きている
3. その間、再生位置（`currentPosition`）がほぼ進んでいない（例：1.5秒で 300ms 未満）
4. 今のトラックが最後まで読み込み済みではない（曲末の正常な遷移と区別する）

条件 3 が大事：本物のネットワーク待ちでも BUFFERING には落ちるが、その場合は READY に何度も戻ったりしない。条件 2 と 3 を両方見て誤検知を防ぐ。

**実装の要点：**
- `onPlaybackStateChanged(state)` で READY→BUFFERING の時刻を `ArrayDeque<Long>` に記録し、1.5秒より古いものを捨てる。
- 4回目が来た時点で、記録しておいた最初の位置と今の位置を比べる。
- 判定は Main スレッドの軽い処理だけにする（`onIsPlayingChanged` は既に重いので、そこには足さない）。

### Step 2：復帰
- `player.seekTo(player.currentMediaItemIndex, player.currentPosition)` を 1回実行する。
- **クロスフェード中は実行しない**：`CrossfadeController` が2台目のプレイヤーを動かしている間は、対象の取り違えを防ぐため見送る。
- **連続実行の制限**：同じ曲で 10秒以内の再実行はしない。1曲につき 3回までとする。それでも詰まる場合は、出力そのものが死んでいるとみなし、ログを残して何もしない（無限シークを防ぐ）。
- 復帰時に `PlaybackLog.note("audio output stalled (N flips in 1.5s, position +Xms): re-opening the output at …")` を出す。「Copy log」で確認できるようにするため。

### Step 3：既存処理との干渉を避ける
- シーク後に Heat（`ListeningRecorder`）が「スキップ」と誤判定しないことを確認する。同じ曲内のシークは曲の切り替えではないので問題ないはず。
- 再生位置の保存（`savePlaybackState`）、ReplayGain、歌詞ティッカーはシークを普通に扱えるので、追加の対応は不要。
- 「再生／一時停止の通知」が往復のたびに更新されないかを確認する。必要ならウォッチドッグ作動中の約 300ms は通知の更新を抑える（任意）。

### Step 4（任意・効果を見てから）：詰まりにくくする
- `DefaultAudioSink.Builder.setAudioTrackBufferSizeProvider(...)` で、USB/Bluetooth 出力時だけ AudioTrack のバッファを大きくする（例：PCM で 250ms → 500ms）。HAL 側の一時的な遅れを吸収しやすくなる。
- ただし再生開始とシークの反応が少し遅くなるので、Step 1〜3 の効果を見てから判断する。

### やらないこと
- Media3 のダウングレード：根本原因ではないため（§1 補足）。比較用の切り分けとしてのみ行う。
- `MediaCodecAudioRenderer` のサブクラス化で `isReady()` を書き換えること：猶予を消しても AudioTrack の詰まりは残るうえ、Media3 の更新に弱い。

---

## 4. 検証手順（adb）

1. **再現環境**：USB-DAC と Bluetooth の両方で長時間再生する（再現しやすい条件があれば記録しておく）。
2. **ログ**：
   - `adb logcat -s AudioTrack AudioFlinger BitChord AUDIO_OUT` を保存しながら再生する。
   - 詰まったときに `AudioTrack` の underrun や `obtainBuffer timed out` などが出ているか見る。HAL 側か、アプリの処理チェーン側かの切り分けになる。
3. **ウォッチドッグの動作確認**：
   - 再現時に「Copy log」で `audio output stalled …` が 1回だけ出て、すぐ再生が戻ることを確認する。
   - 疑似的に起こすなら、再生中に DAC を抜き差しする、Bluetooth を遠ざける、など。
4. **誤検知がないことの確認**：
   - ネットワーク曲を機内モードで再生する（本物のバッファ待ち）→ ウォッチドッグが作動しないこと。
   - 曲の終わり、ギャップレス、クロスフェード、リピート1曲 → 作動しないこと。
5. **比較（任意）**：Media3 1.10.1 のビルドで同じ条件にしたときの挙動を記録する（周期が変わるだけか、止まり方が変わるか）。

---

## 5. 作業量の目安
| 内容 | 目安 |
|---|---|
| Step 1〜3（ウォッチドッグ＋ログ） | 半日 |
| 実機検証（USB-DAC／BT、誤検知確認） | 半日〜1日（再現頻度次第） |
| Step 4（バッファサイズ調整） | 必要なら半日 |
