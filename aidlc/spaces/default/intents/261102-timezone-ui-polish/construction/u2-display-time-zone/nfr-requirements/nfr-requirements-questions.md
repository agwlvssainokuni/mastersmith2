# NFR Requirements の質問（U2 u2-display-time-zone）

U2 は、表示のタイムゾーンの設定の API（利用者の表の列を1つ足す）・プリファレンスの選択・配布・管理の画面の時刻の単位です（`unit-of-work.md` の種類は service。アプリと画面の両方に入る）。この単位に当たる NFR は、セキュリティ（本人だけ・一括代入・漏えい・信頼できない入力）、性能（ログイン後の読み取り1回・選択肢の組み立て・保存の費用）、アクセシビリティ（選択のキーボード操作・読み上げ・axe）、互換と戻し（列を足すだけ・E2E の見本の形）、テスト、言語です。拡張（負荷の増え方）と観測（新しい指標・警報）は新しい経路を作らないため、短い文書にとどめ、新しい決まりは足しません（理由は下の表）。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| 本人だけ・一括代入 | 表示のタイムゾーンは本人だけが読み書きする。未認証は 401。本文の利用者 ID・他人の設定・停止や管理者の印などは読み捨て、自分の行だけが変わる。管理者が他人の分を変える API は作らない。サーバー側のテストで確かめる | 要件 NFR1.2・BR2.7 |
| 内部DB の変更 | 利用者の表に NULL 可の列を1つ足すだけ（前進のみ・既定なし）。前の版のアプリは足した列を読まずに動く。前の版の PUT（5つ目を送らない）は保存済みの値を変えない。移行の最後の版を前提にしている既存のテストの洗い出しは Code Generation の計画で行う | 要件 NFR4.1・BR2.1・BR2.10・`team.md` Deployment |
| 問い合わせの数・目標 | 既存の目標（`preferencesGet`・`preferencesSave`・`preferencesInvalid` の p95 1 秒）は変えず緩めない。保存は同じ文の中で列を1つ足して書くだけで、問い合わせを足さない。ログインの後の読み取りは1回だけ（画面の移動・再描画では読み直さない） | 要件 NFR2.1・BR2.14・AC5.3.3 |
| 漏えい | 無効な入力の文字列を、アプリのログ・トレースの属性・エラー応答に載せない（入れた値は応答・ログに載せない）。検証を通る前の値を持つ型は `toString` で伏せる（レビュー R-03 の解消。BR2.4 と BR2.9 の食い違いは、この段の要件で「検証を通る前の値は伏せる」に統一する）。TRACE を有効にした `XxxSecretLeakIT` で確かめる | BR2.4・BR2.9・`team.md` Code Style |
| 名前の集合の照合 | 集合は `ZoneId.getAvailableZoneIds()` の完全一致。JDK の API は呼び出しごとに集合の写しを作る見込み（未検証）のため、要求ごとに作り直さず、不変の集合を1回だけ持つ。純粋な関数にして jqwik の性質ベースのテストを付ける | BR2.2・`unit-of-work.md` |
| アクセシビリティ | 選択は make-you-chic-ui の `Select`（標準の `<select>`）を使う。WCAG 2.1 AA。axe は、ブランドカラーとテーマのすべての組・誤りの状態・解釈できない名前の Alert・長い名前の選択肢で違反 0 件。規則は外さない。画面部品ごとに vitest-axe を1件 | 要件 NFR3.1・NFR3.3・BR2.23・`project.md` Forbidden |
| 互換（画面・E2E） | 5つ目の `displayTimeZone` を足す変更で、E2E の見本（`frontend/e2e/support/preferencesFixtures.ts` の `preferencesSample`・`hasPreferencesShape`）、差し替えの回数（080 の `toBe(1)`、100 の `gets` の `toBe(1)`）、110 の 401 を待つ流れ、管理の画面の単体テストの `timeZone` の渡し方が影響を受ける。件数の洗い出しは Code Generation の計画で行い、この段では要件として取り込む（レビュー R-01） | レビュー R-01・`team.md` Testing Posture |
| E2E の本数 | U2 は E2E の流れを足さない（N6 の代表の流れ1本は U4）。既存の E2E の書き換えと axe の状態の追加は本数に数えない | 要件 NFR5.3・`team.md` |
| テストのタイムゾーン | 画面のテスト（Vitest）と E2E（Playwright の `timezoneId`）は時差を固定し、別の時差を確かめるテストはその時差を明示に指定する。ブラウザの既定のタイムゾーンを暗に使わない検査は構造の検査を1つに決める（レビュー R-04） | 要件 NFR5.2・`team.md` |
| 言語 | 足す画面の文言は日本語と英語の両方（既定は日本語）。IANA の名前と今の時差を `Asia/Tokyo (+09:00)` の形で示す | 要件 NFR6.1・BR2.24 |
| この単位に当たらない NFR | NFR1.1（自作の SVG）は u6、NFR1.3・NFR2.2・NFR4.2（メニューの応答・ホーム）は u5、NFR3.2（アイコン）は u4・u5・u6。NFR5.1 の日時の必須のテストのうち、型・端数・範囲の端は u3、変換の関数の性質ベースは u1（U2 は画面を通した夏時間の地域の確かめだけを受け持つ） | 要件・`unit-of-work.md` |
| 拡張・信頼性・観測 | 新しい経路は、既存の `/api/me/preferences` の項目を1つ足すことと、画面のログイン後の読み取り1回だけ。既存の HTTP 指標・警報（5xx の割合・p95）が新しい項目を含めて効くため、新しい指標・警報・ダッシュボードは足さない。可用性の目標は既存のまま。読み込みの失敗は画面の `error` 状態（BR2.15）で受け、再読み込みで回復する | 要件・BR2.15 |
| 測っていない目標 | 性能の合格は、測っていないものを「満たした」にしない。この Intent の流れに Performance Validation の段が無く、使い捨ての環境を手元で用意できるため、k6 の測りと確かめの持ち主は Build and Test とする（`project.md` の決まり）。それまでは Deferred | `project.md` Testing Posture |

## Q1. [品質] 選択肢（約 400 個）の組み立ての時間の目標と測り方

文脈: プリファレンスの選択肢は、ブラウザが返す名前（約 400 個）それぞれに、今の時差（`+09:00`）を添えて作ります（BR2.17）。今の時差は U1 の `formatInstant` に注入できる現在時刻を渡して得るため、作る費用は U1 の整形 400 回分です。U1 の目標は「1つの値の変換が平均 0.05 ミリ秒以内」なので、組み立ては 20 ミリ秒前後の見込みです（未測定）。画面を開くたびに作ると遅れが出るため、一覧と現在時刻が変わらない間は作り直しません。要件 NFR2.1 は「今の性能の目標を変えない」だけで、この画面の数値は決まっていません。

**推奨: A。** 理由: U1 の目標から導いた見込み（20 ミリ秒）に 5 倍の余裕を取った 100 ミリ秒なら、測りが重い環境でも不安定なテストになりにくく、遅い作りに退化したときは確実に落ちます。関数に一覧と現在時刻を注入して固定すれば、機械の既定のタイムゾーンと時計に預けずに測れます。描画（`<option>` 400 個）の時間は jsdom では実際のブラウザと違うため目標にせず、値を記録するだけにします（目標を緩めるのではなく、測れないものを測ったことにしない）。

- A. 組み立て1回（約 400 個と UTC）が、画面のテスト（Vitest・jsdom）の環境で合計 **100 ミリ秒以内**。一覧と現在時刻を注入して固定し、`performance.now()` の差を測る（測った環境の Node の版・CPU を成果物に書く）。あわせて「入力が変わらない間は作り直さない」（`formatInstant` の呼び出し回数が増えない）を単体テストで確かめる。描画の時間は記録だけで目標にしない。
- B. 合計 **50 ミリ秒以内**（U1 の目標の 2.5 倍）。ほかは A と同じ。厳しい分、重いテストの機械では不安定になりうる。
- C. 時間の目標は置かず、「入力が変わらない間は作り直さない」の確かめだけにする。遅い作りに退化しても気づけない。
- X. Other (please specify)

[Answer]: A

## Q2. [品質] 負荷の試験（k6）に足す場面

文脈: 既存の場面 `preferencesGet`（GET）・`preferencesSave`（PUT。氏名・言語・テーマ・文字の大きさの4項目）・`preferencesInvalid`（言語 `xx` の 400）が p95 1 秒で `perf/k6/scenarios.js` にあります。前の版の形の4項目の PUT は新しい版でも保存済みの表示のタイムゾーンを変えないため、`preferencesSave` は新しい列を書く経路を通りません。新しく通るのは「5項目の PUT（名前の保存・null で消す）」と「名前の照合による 400」です。ログイン後の GET 1回は `preferencesGet` で負荷の形を確かめられます。この Intent は Performance Validation の段が無く（スキップ）、使い捨ての環境を手元で用意できるため、持ち主は Build and Test です。

**推奨: A。** 理由: 新しい列を書く経路と名前の照合は、既存の場面では通らないため、場面を1つだけ足せば測っていない経路を残さずに済みます。名前の照合の費用（集合の写しを作る見込み）は 400 の経路で効くため、成功と拒否を同じ場面の中で交互に流すと1つで両方を測れます。目標は既存と同じ p95 1 秒で、緩めません。

- A. 場面 `preferencesSaveTimeZone` を1つ足す: 5項目の PUT を、回ごとに「名前の保存（200）」「null で消す（200）」「無効な名前（400 `VALIDATION_FAILED`）」で交互に流し、成功の `name` ごとに p95 1 秒・`checks` の率 1。setup は前の場面の状態に頼らず、自分で前提を作る。既存の3場面は変えず、`preferencesGet` は 5項目の応答でも `checks` が通るようにする。
- B. 場面は足さず、既存の3場面を新しい版でもう一度流して、目標を変えていないことだけを確かめる。新しい列を書く経路と名前の照合の費用は測らない。
- C. 場面を2つ足す: 成功（名前・null）の `preferencesSaveTimeZone` と、拒否の `preferencesInvalidTimeZone` を分け、それぞれ p95 1 秒。成功と拒否を別々の値で見られるが、台本が増える。
- X. Other (please specify)

[Answer]: A

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`・`functional-spec.md`・`reviews/review-01.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（NFR1.1〜NFR6.1）
- 手本: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/`・`u3-target-db-time/nfr-requirements/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `ZoneId.getAvailableZoneIds()` が呼び出しごとに集合の写しを作る、という見立ては未検証（NFR 設計の捨ての試しで確かめる）。作らないなら「不変の集合を1回だけ持つ」は保険として残るだけで、要件は変わらない。
- [assumption] ブラウザの名前の一覧は約 400 個（機能設計の見積もり）。実測の個数は Q1 の測りで記録する。
- [assumption] 場面の足し方（Q2）は、`perf/README.md` の「利用者の設定と招待の場面」の形（`checks` の率 1 と name ごとの p95）に従う。
- 承認の場で依頼者に諮る点（質問にはしない）: 機能設計のレビュー R-02（AC1.2.4 と BR2.22 の食い違い。保存済みの名前を解釈できないとき、管理の画面は案内を出さずブラウザのタイムゾーンで出す）を、この段の成果物の「上流との差」に正直に記録する。
