# 技術の選択とテストの要件 — u2-display-time-zone

出典: 機能設計（`functional-design/rules.md`・`functional-spec.md`・`frontend-components.md`）、要件（`inception/requirements-analysis/requirements.md` の NFR5）、質問と答え（`nfr-requirements-questions.md`: Q1: A・Q2: A）、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）、`team.md`（Code Style・Testing Posture・Deployment）、`project.md`（Tech Stack・Forbidden・Mandated）、既存のコード（読み取りだけ: `frontend/eslint.config.js`・`vendor/make-you-chic-ui` の `Select`）。

番号の付け方は `security-requirements.md` の冒頭のとおり。NFR5 の細目を NFR5.1.x・NFR5.2.x・NFR5.3.x に置く。

## 1. 技術の選択

| 項目 | 選択 | 理由 | 検討した別の案 |
|---|---|---|---|
| 内部DB の保存 | 利用者の表に NULL 可の列を1つ（`display_time_zone`・`VARCHAR(64)`）。移行は Flyway のファイル1つ（前進のみ）。保存は既存の JPA の更新の経路に列を足す（keep のときは書かない） | 新しい表・索引が要らず、前の版が読まずに動く（NFR4.1）。64 文字は IANA の名前の最長を十分に超える | 設定の専用の表: 結合が増え、戻しの対象が増える。ブラウザの保存先（`localStorage`）: 端末ごとに違い、サーバーが持つ前提（本人だけが読み書き）に合わない |
| 名前の検証 | JDK の `ZoneId` が持つ地域の名前の集合への、大文字小文字を区別した完全一致。集合は不変の集合を1回だけ持つ。純粋な関数（時計・DB に触れない）にして jqwik の性質ベースのテストを付ける | 画面の選択肢（ブラウザ）よりサーバーの集合が広く、ブラウザが解釈できない名前は `saved-unresolvable` で受けるため成り立つ。集合を作り直さない（`performance-requirements.md` の NFR2.1.5） | `ZoneId.of` で解釈を試す: 別名・オフセット表記・地域の付いた表記を受けてしまい、保存する値が揺れる |
| 3状態（変えない・消す・置く）の受け取り | 本文の項目の有無と `null` を区別できる受け方を Code Generation で決める（今の `Preferences` の4つの組は変えない） | 前の版の画面と互換に保つため（BR2.3・BR2.5） | 項目が無いときも `null` として消す: 前の版の PUT が値を消す |
| 配布 | 画面の側の提供元（React のコンテキスト）。ブラウザのタイムゾーンを読む場所は配布の部品の1か所（差し替えられる関数）。変換は時刻変換の部品（U1）だけを使う | 契約 C2 と BR2.13 | 画面ごとに読む: 暗黙の既定のタイムゾーンが各所に入る |
| 構造の検査 | ESLint の規則1つ（`security-requirements.md` の NFR1.4.6）。許す場所は配布の1か所。規則の確かめは ESLint の `Linter` API を使う Vitest の単体テスト | レビュー R-04 の決め。`frontend/eslint.config.js` に時刻変換の部品用の決まりが既にあり、同じ置き方を広げる | 単体テストでソースを走査する: 規則が二重になり、片方の取り残しが出る |
| 選択の部品 | make-you-chic-ui の `Select`（標準の `<select>` ベース）・`FormField`・`Alert`。上流に変更を頼まない | キーボードと読み上げの動きが標準で足りる（NFR3.3）。選択肢が約 400 個でも標準の選択で足りる | 検索つきの選択の自作: アクセシビリティの検査と保守が増える |
| 新しい依存 | 足さない（NFR1.5.1） | `team.md` Code Style | — |
| 負荷の試験 | 既存の k6（`perf/k6/scenarios.js`）に場面 `preferencesSaveTimeZone` を1つ足す（Q2: A）。既存の閾値の部品・伏せ字の仕組みを使う | `performance-requirements.md` の NFR2.1.4 | 場面を足さない・2つに分ける（質問で退けた） |

## 2. テストの要件（NFR5）

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR5.1.1 | 夏時間の地域と端数の時差を、画面を通した形で確かめる: 現在時刻を注入して1月と7月で選択肢の時差が切り替わる（夏時間の地域は `-05:00` と `-04:00` のように）、分の端数を持つ時差（`+05:45`・`+09:30`・`-03:30`）、UTC が一覧に無いときの足しと重複させないこと、保存済みの名前が一覧に無いときの足し、保存の成功で開いている管理の画面の時刻が切り替わること、保存済みの名前を解釈できないとき（`saved-unresolvable`）管理の画面がブラウザのタイムゾーンで時差つきの時刻を出すこと、固定のオフセットは解釈できない名前として扱うこと。変換の関数そのものの性質ベース・日付の変わり目・端数は、変換の部品の単位が持つ | NFR5.1（夏時間の地域の確かめは変換の部品の単体テストに限らず画面を通した形でも行う）・`team.md` Testing Posture | 画面の単体テスト（時差と現在時刻を明示に固定。機械の既定に預けない）と、実際のブラウザ（Playwright の `timezoneId`）での管理の画面の時刻の確かめ（既存の流れの書き換えの中で行い、本数に数えない） |
| NFR5.1.2 | 性質ベースのテスト（失敗時の乱数の種を記録）: サーバー側は jqwik で名前の照合（`security-requirements.md` の NFR1.4.1）。画面側は fast-check で選択肢の組み立て（先頭が「ブラウザに合わせる」、残りが名前の昇順で重複なし、保存済みの名前は常に選択肢にある、ブラウザが知らない名前は並ばない、UTC は重複しない）と、分までの書式への切り出し（元の整形の文字の年月日・時分・時差を保つ） | `team.md` Testing Posture（性質ベース） | `./gradlew verify`（jqwik）と画面のテスト（fast-check） |
| NFR5.1.3 | 認証・認可・監査の必須のテスト（`team.md`）のうち、この単位が受け持つもの: 本人だけが変えられる（NFR1.2.1）、未認証 401、要求の本文の改ざん（NFR1.2.2）、停止中の拒否（NFR1.2.3）、3状態（NFR1.4.2）、監査に残らない（NFR1.4.4）、秘密・値の漏えい（NFR1.4.3）、400 の形と全体を保存しない（NFR1.4.1）。403 を確かめる利用者は、要る権限だけを欠く利用者にする必要がある API は無い（`/api/me/**` はログインだけ）ため、「権限の無い利用者」の確かめは置かず、ログインだけの分類が変わらないことを確かめる | `team.md` Testing Posture | 結合テスト（`./gradlew verify`） |
| NFR5.1.4 | カバレッジの下限（行 80%・分岐 70%）を、バックエンド（パッケージごと。`user` の機能のパッケージは `packagesJudgedByTotal` の一覧に無く、パッケージごとの下限の対象）と画面（全体の合計）で満たす。計測から外すものを増やさない。新しい機能のフォルダ（`src/app/display-settings/` と時刻を出す部品）のカバレッジの値は Build and Test で記録する。テストの件数・カバレッジを報告するときは `:backend:cleanTest :backend:cleanIntegrationTest` を付けて測り直す | `team.md` Testing Posture・`project.md` Testing Posture | `./gradlew verify`（JaCoCo・`@vitest/coverage-v8`）。値は Build and Test に記録 |
| NFR5.2.1 | テストのタイムゾーンを固定する: 画面のテスト（Vitest）は時差を固定し、配布の提供元・ブラウザのタイムゾーンの取り出し・現在時刻を差し込んで、機械の既定（手元は `Asia/Tokyo`、CI の機械は UTC）に結果を預けない。別の時差を確かめるテストはその時差を明示に指定する。サーバー側のテストの名前の照合は時差・時計を使わない | NFR5.2・`team.md` Testing Posture | 機械の既定を変えて流しても結果が変わらないこと（手元と CI の差で落ちない） |
| NFR5.2.2 | 描画の後の効果で反映される値（配布の `ready`・選択の保存値・差し替え）は、操作の直後に同期で確かめず `waitFor` で待って確かめる。上限の時間（Vitest の既定 5 秒）は、原因を確かめずに延ばさない。待ち合わせは時間に預けず、取得の関数の解決を注入して決定的に作る | `team.md` Testing Posture（画面のテスト） | 画面の単体テストの書き方。負荷の高い CI での失敗を作らない |
| NFR5.2.3 | 実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）をこの単位は変えない。変えるときは4か所をそろえて配備の記録に書く | AC5.3.4・`team.md` Deployment | コード生成の差分にその4か所の変更が無いこと |
| NFR5.3.1 | この単位は E2E の流れを足さない（日時の代表の流れ1本は業務データの単位が受け持つ）。既存の E2E の書き換え（見本・差し替えの回数・管理の画面の期待）と、axe の状態の追加（解釈できない名前・誤り・長い名前・読み込み中・読み込み失敗）は本数に数えない。書き換えた E2E と axe の違反 0 件を、件数で Build and Test に記録する | NFR5.3・`team.md` Testing Posture | E2E の全件の通過と違反 0 件を件数で記録（Build and Test） |

## 3. 規模の見積もり（テストの時間）

- `./gradlew verify` の時間と CI の時間は、テストを足した Bolt の後と Build and Test で測って記録する。CI の上限（`timeout-minutes: 60`）の7割（約 42 分）を超えたら、テストの分け方を依頼者に諮る（`team.md`）。この単位が足す結合テストは小さい（内部DB の H2 だけ。コンテナを使わない）ため、時間への影響は小さい見込み（未測定）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 3状態の受け取り方（項目の有無と `null` の区別）の具体は Code Generation で決める。要件は「前の版の PUT が値を消さない」まで。
- [assumption] 画面のテストの時差を固定する既存の仕組み（Vitest の設定）があればそれを使い、無ければ Code Generation の計画で決める。
- [assumption] 場面 `preferencesSaveTimeZone` の閾値の部品は、`perf/k6/thresholds-test.sh` の既存の形で足せる。
- 未解決の点は無い。
