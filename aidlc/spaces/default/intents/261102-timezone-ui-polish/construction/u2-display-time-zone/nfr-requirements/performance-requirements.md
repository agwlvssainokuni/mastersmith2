# 性能の要件 — u2-display-time-zone

出典: 機能設計（`functional-design/rules.md` の BR2.3・BR2.14・BR2.17・BR2.19〜BR2.21、`functional-spec.md` の 6 節）、要件（`inception/requirements-analysis/requirements.md` の NFR2.1）、質問と答え（`nfr-requirements-questions.md`: Q1: A・Q2: A）、`perf/README.md` と `perf/k6/scenarios.js` の既存の場面（読み取りだけ）、`team.md`・`project.md` の Testing Posture。

番号の付け方は `security-requirements.md` の冒頭のとおり。NFR2.1 の細目を NFR2.1.1〜NFR2.1.7 に置く。

**確かめの区分**: 「テスト」は `./gradlew verify` の中で毎回確かめるもの。「Deferred」は、負荷の環境（使い捨ての環境）か実測が要り、この段では**測っていない**もの。この Intent の流れには Performance Validation の段が無く、使い捨ての環境を手元で用意できるため、Deferred の持ち主は Build and Test とする（`project.md` の決まり）。測っていない目標を満たした扱いにしない。

## 1. 数の目標

| ID | 要件 | 根拠・決まり | 確かめ方 | 区分 |
|---|---|---|---|---|
| NFR2.1.1 | ログインした後の `GET /api/me/preferences` は1回だけ。ログイン（Cookie での復元を含む）のたびに1回で、画面の移動・再描画・タブの切り替え・保存の成功では読み直さない（保存の応答の値で差し替える）。読み込みが失敗したときの再読み込みだけが追加の読み取りになる。ポーリングは作らない | NFR2.1・BR2.14・BR2.16・AC5.3.3 | 画面の単体テスト: 取得の回数を数え、ログインの後に1回、画面の移動と再描画で増えないこと。保存の成功で増えないこと。遅れて届いた古い答えを捨てても回数が増えないこと | テスト |
| NFR2.1.2 | 内部DB への問い合わせの数を増やさない。読み取りは今と同じ数（利用者の行の読み取り）、保存は今と同じ数で、表示のタイムゾーンの列を同じ書き込みに含める（keep のときは書かない）か、書く場合も追加の読み取り・存在確認の問い合わせを足さない。名前の照合は集合への完全一致だけで、DB に問い合わせない | NFR2.1・BR2.3・BR2.6 | 結合テスト（既存の文の数を数えるテスト、`GroupAdminListQueryCountIT` と同じ形）: 読み取り・保存（項目なし・null・名前・無効な名前）で、実行された文の数が今の4項目の保存の数を超えないこと。今の数は Code Generation の計画で実測して決め、計画に書く | テスト |
| NFR2.1.3 | 既存の目標を変えず、緩めない: `preferencesGet`・`preferencesSave`・`preferencesInvalid` の p95 は 1 秒、`checks` の率 1。新しい版でも、4項目の PUT・5項目の GET が同じ目標で通る | NFR2.1・AC5.3.3・`perf/README.md` | k6 の既存の3場面を新しい版で流し、目標を満たすこと | **Deferred（Build and Test）** |
| NFR2.1.4 | 新しい場面 `preferencesSaveTimeZone` を `perf/k6/scenarios.js` に1つ足す（Q2: A）: 5項目の PUT を、回ごとに「名前の保存（200）」「`null` で消す（200）」「無効な名前（400 `VALIDATION_FAILED`）」で交互に流す。判定は、成功と拒否を `name` で分けた p95 1 秒（緩めない）と、場面ごとの `checks` の率 1。setup は前の場面が残した状態に頼らず、自分で前提を作る（片付けてから始める）。使い捨ての環境の試験用の利用者（`perf-user01`〜）で流し、本物のデータと監査を汚さない。結果の記録に認証の値を置かない（伏せる）。台本の読み込みは `k6 inspect`（`--include-system-env-vars` を付ける）で、閾値の部品は `perf/k6/thresholds-test.sh` で確かめる | NFR2.1・Q2: A・`team.md`/`project.md` の k6 の決まり | 台本と閾値は Code Generation で書き、`./gradlew verify` の範囲（閾値のテスト）と `k6 inspect` で確かめる。測りは使い捨ての環境で実行 | 台本と閾値: テスト。測り: **Deferred（Build and Test）** |
| NFR2.1.5 | 名前の集合は不変の集合を1回だけ持ち、要求ごとに作り直さない（JDK の API は呼び出しごとに集合の写しを作る見込みで、未検証）。照合は集合への完全一致の探索だけ | NFR2.1・BR2.2 | 単体テスト: 同じ判定器で何度照合しても集合の生成の回数が増えないこと（生成を数える差し込み口を持つ形にするかは Code Generation で決める）。捨ての試しで、API が写しを作るかを確かめ、結果の要点（JDK の版・結果）を成果物に写す | テスト（試しは NFR Design） |
| NFR2.1.6 | プリファレンスの選択肢（ブラウザが返す名前の約 400 個と UTC に、今の時差を添える）の組み立て1回が、画面のテスト（Vitest・jsdom）の環境で合計 **100 ミリ秒以内**（Q1: A）。一覧と現在時刻を注入して固定し、`performance.now()` の差を測る。測った環境（Node の版・CPU）と名前の個数を成果物に書く。一覧と現在時刻が変わらない間は作り直さない（時刻変換の部品の整形の呼び出し回数が増えない）。描画（`<option>` 約 400 個）の時間は jsdom では実際のブラウザと違うため目標にせず、値を記録するだけにする | BR2.17・Q1: A | 単体テスト（時刻と一覧を注入し、機械の既定のタイムゾーンに頼らない）: 組み立ての時間と、2回目の呼び出しで整形の回数が増えないこと | テスト（描画の時間は記録のみ） |
| NFR2.1.7 | 管理の画面の時刻の整形は、時刻変換の部品を1つの時刻につき1回だけ通し、読み込み中・読み込み失敗のときは整形しない（時刻の欄を出さない）。利用者の一覧・招待の一覧の1ページ分の描画で、整形の呼び出し回数が表示する時刻の数を超えない。分までに落とす薄い関数は文字の切り出しだけで、費用の目標は時刻変換の部品の目標（1つの値の変換が平均 0.05 ミリ秒以内、1ページ最大 1 万個で 500 ミリ秒以内）の中に入る | BR2.19〜BR2.21・NFR2.1 | 画面の単体テスト: 整形の呼び出し回数を数え、loading・error のとき 0 回、ready のとき時刻の数以下。1ページ最大の行数の時刻を整形して時刻変換の部品の目標の範囲に収まること | テスト |

## 2. 負荷の環境が要るもの（Deferred の一覧）

| 確かめること | 場面・手段 | 持ち主の段 | 状態 |
|---|---|---|---|
| 既存の3場面の p95 1 秒・`checks` 率 1（NFR2.1.3） | k6 の `preferencesGet`・`preferencesSave`・`preferencesInvalid` を使い捨ての環境で流す | Build and Test | Deferred（未測定） |
| 新しい場面の p95 1 秒・`checks` 率 1（NFR2.1.4） | k6 の `preferencesSaveTimeZone` | Build and Test | Deferred（未測定） |
| ログイン後の読み取り1回が実ブラウザで1回であること（NFR2.1.1 の実機の確かめ） | 既存の E2E の流れで取得の回数を数える（流れは足さない） | Build and Test（E2E の実行） | Deferred（単体テストでは確かめる） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`・`functional-spec.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 時刻変換の部品の目標（1つの値の変換が平均 0.05 ミリ秒以内）は、その単位の測りで満たされる前提。満たされなければ NFR2.1.6 の 100 ミリ秒の根拠（約 20 ミリ秒の見込み）が崩れるため、測った値で見直す（目標を緩めるときは理由を記録する）。
- [assumption] 約 400 個という名前の個数は機能設計の見積もり。実測の個数は NFR2.1.6 の測りで記録する。
- [assumption] 問い合わせの文の数（NFR2.1.2）は、今の保存と読み取りの数を Code Generation の計画で実測して決める。この段では数字を置かない。
- 未解決の点は無い。
