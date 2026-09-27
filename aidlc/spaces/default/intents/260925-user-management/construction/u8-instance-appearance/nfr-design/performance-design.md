# Performance Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の1本の API `GET /api/appearance`（契約 C7）の性能の設計です。答えは `nfr-design-questions.md`（Q1: A、Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR はこの単位の NFR 要件 `construction/u8-instance-appearance/nfr-requirements/` の枝番（枝番はこの単位の中で振る）、BR は `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」は `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号、U4 W2 は `construction/u4-display-foundation/functional-design/functional-spec.md` の W2。

## 1. 性能の予算

| 対象 | 目標 | 設計で割り当てる内訳 | 出典 |
|---|---|---|---|
| `GET /api/appearance`（トークンなし） | 同時 10 件で p95 300 ミリ秒以内 | 要求の処理の中に I/O が無い（内部DB・ファイル・外部の呼び出しなし）。時間のほとんどは既存のフィルターの連鎖（トレース・ヘッダー・キャッシュの見出し・認可）と JSON にする処理で、U8 の中の処理は2つの値を DTO に写すだけ | NFR6.1、要点 3 |

U4 W2 のとおり、この応答はすべての画面の最初の描画を待たせるため、「I/O を持たない」ことを予算の前提として守る。I/O を足す変更（設定の読み直し・内部DB への保存など）は、この予算を崩すため設計の変更として扱う。

## 2. 起動時の解決と要求の処理

- 判定と警告のログは、業務処理の Bean（`service`）を作るときに1回だけ行う。設定の型（`config` の `record`）をコンストラクター注入で受け取り、その場で判定して、結果を変わらない `record` としてフィールドに持つ（NFR6.2、BR1.6）。
- 要求の処理（`web` の受け口）は、Bean が持つ `record` の2つの値を応答の DTO に写して返すだけ。設定を読み直さず、判定もしない（NFR6.2）。
- 応答の本文は `brandColor`・`fontFamily` の2項目だけの小さな JSON（NFR6.2、BR3.1）。

説明用の断片（名前はコード生成で決める）:

```java
// service: 起動時に1回だけ解決して持つ
public AppearanceService(AppearanceProperties properties) {
    this.current = AppearanceResolver.resolve(properties, LOG); // 判定と警告はここだけ
}
public ResolvedAppearance current() { return current; }        // 要求のたびはこれだけ

// web: 写して返すだけ
@GetMapping("/api/appearance")
public AppearanceResponse get() { return AppearanceResponse.from(service.current()); }
```

## 3. キャッシュ

- サーバー側のキャッシュは置かない。値はもとからメモリにある変わらない値で、キャッシュで稼ぐものが無い。
- 応答のキャッシュの見出しは既存の `CacheControlFilter`（`/api` の下は `no-store`）のまま変えない（NFR4.8、BR3.6）。見た目の設定は起動し直しで変わり、`no-store` なら変更の後の最初の読み取りから新しい値になる。前の値で一瞬描かれることは U4 W2 のゲートで防ぐ。

## 4. 資源の使い方

| 資源 | 使い方 | 出典 |
|---|---|---|
| 内部DB の接続 | 借りない（`reliability-design.md` の2節・3節で確かめる） | NFR5.1・NFR5.2 |
| スレッド | 既存の要求のスレッドだけ。非同期の処理・独自のスレッドプールを持たない | NFR6.3 |
| メモリ | 2つの短い名前の `record` 1つ | NFR6.2 |

## 5. 測り方

- 測定の持ち主は performance-validation の段。既存の `perf/k6/scenarios.js` に見た目の設定の場面を1本足し（場面の名前はコード生成で決める）、同時 10 件で 95 パーセンタイルを出す。全件が 200 で本文が2項目であることを checks で確かめる（NFR6.1）。
- 使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で、内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。長い試験は `caffeinate -i` を付ける（`aidlc/spaces/default/memory/project.md` の Testing Posture）。この API は監査に残らないため、試験で監査ログは増えない。
- 「要求の処理で判定しない」ことは、単体テスト（要求を2回処理しても判定の関数は Bean の作成の1回だけ呼ばれる）とコード生成のレビューで確かめる（NFR6.2）。
- 目標を満たせなかったときは、目標を緩めずに原因をログと状態で確かめてから記録する。

## 6. 上流との差

承認済みの文書と食い違う設計は無い。
