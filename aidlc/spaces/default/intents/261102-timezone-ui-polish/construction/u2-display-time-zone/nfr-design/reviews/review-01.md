## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/traceability.json > upstream_ids と coverage | 前の段（nfr-requirements/traceability.json）が持つ上流の NFR のうち NFR1.1・NFR1.3・NFR5.1・NFR5.3 の行が、この段の追跡に無い。本文は NFR5.1.x と NFR5.3.1 を受け持つと書いており（logical-components.md の NFR のパターンの表、security-design.md の SD9 の表の NFR5.1.1〜5.1.3）、要件の連鎖が NFR5.1・NFR5.3 で切れる。代わりに上流に無い NFR1.4・NFR1.5・NFR2.3・NFR4.3 が最上位の行として並び、前の段の最上位の行の形（上流の ID を最上位、単位で足した枝番は target 内）とそろっていない | NFR5.1（OK、割り付け先 SD9）と NFR5.3（OK、E2E の流れを足さない）の行を足す。NFR1.1 と NFR1.3 は N/A で所有者の単位だけを書いて足す。NFR1.4 などの単位で足した枝番は、上流の最上位の行（NFR1.2・NFR2.1・NFR4.1 など）の target に寄せるか、上流との対応を冒頭に明記する | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/logical-components.md > 承認済みの記録との差 D9 と Assumptions & Open Questions | NFR2.1.7（整形の呼び出しは表示する時刻の数を超えない）を「表示する時刻の数 + 1」と読む緩めの具体化を、質問にせず決めている。表では「承認の場で伝える」とあるが、「承認の場で依頼者に確かめる点」には D8 しか挙がっていない。承認済みの要件の数の上限を動かす差は、確かめてから書く決まり | D9 を承認の場で確かめる点に加える。または NFR2.1.7 を満たす形（今の時刻の整形を表示する時刻に数えない定義を要件側に残す）を選択肢として併記する | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/logical-components.md > 後の段へ引き継ぐこと と reliability-design.md > RD2 | Code Generation の計画で洗い出す検索の一覧に、`updatePreferences` の呼び出し側が無い。保存の 1 文（LC3）は戻り値を行数（int）から 2 列の結果に変えるため、UserRepositoryIT（updatePreferences を 5 か所で呼び、4 列だけ触れる確かめを含む）と UserPreferencesServiceTest（when・verify で updatePreferences を固定）が壊れる。RD2 が触れるのは MePreferencesLockTimeoutLeakIT の説明の名前だけ | 検索の一覧に `updatePreferences` と `UserRepositoryIT`・`UserPreferencesServiceTest` を加える。「4 列だけ触れる」確かめは「5 列だけ触れる」へ期待を緩めずに移すと書く | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/security-design.md > SD6 の取りこぼし | 構造の検査の取りこぼしの一覧に、`timeZone` の項目はあるが値が未定義になりうる書き方（`{ timeZone }` で省略可の引数を渡す）が無い。選択子は項目の有無しか見ないため通る。消す予定の frontend/src/shared/format/formatDateTime.ts と features/useradmin/lockedUntil.ts がまさにこの形（`timeZone?: string` を `{ timeZone, … }` で渡す）で、同じ書き方が新しい時刻の欄の部品に入ると規則をすり抜ける | 取りこぼしに追記し、補いとして時刻の欄の部品と選択肢の組み立ての `timeZone` の引数の型を省略不可の `string` にすることと、機械の既定のタイムゾーンを変えて同じ結果になる単体テスト（NFR5.2.1）を挙げる | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる突き合わせ（Read と検索のみ。git・gradlew・docker・npm は未使用） | 実行済み | 前の段のレビューの Minor 3 件は解けている。R-01（NFR2.1 が OK のまま）は追跡の NFR2.1 を Deferred にして持ち主 build-and-test と測っていない部分を target の冒頭に書いた。R-02（100 ミリ秒の揺れ）は 5 回の中央値・失敗時に 5 回の値と環境を出す・100 ミリ秒は延ばさない（PD4）。R-03（誤検知と src/shared/ の漏れ）は範囲を src/ のテストでないファイル全体にし、数値の整形を許す書き方を決めた（SD6。いまの src/ に toLocale 系は 0 件で一致）。目標は緩んでいない |
| D1（更新と結果を 1 文）の実物との突き合わせ | 成立 | 今の保存は UserRepository の JPQL の更新 1 文（行数だけ返す。UserPreferencesService は読み直さず要求の値を返す）。`Keep` で保存後の列を返すには読み取りが 1 つ増えるため、1 文で返す設計は NFR2.1.2 を緩めずに両立する唯一の形。Spring Data 上の動きが未確認な点と、確かめられないときは緩める前に依頼者に諮る旨は、D1 と Assumptions & Open Questions の両方に明記されている |
| 3 状態・名前の集合・配布 | 妥当 | JsonNode（T3）で無い・null・文字列・文字列でない型を分けられ、contains(null) の NPE は文字列だけを門に入れて避ける（SD1）。名前の集合は T1 の写しの費用に基づく不変の 1 つ。配布は WorkRoleProvider（App.tsx で AdminForbiddenProvider の内側）と同じ並びと世代の規則で、useReloadTriggers を使わず 1 回（PD1・RD4）。T2 は Intl の部分だけの測りで、解析の上乗せと jsdom は未測定と正直に書き、Code Generation に回している |
| 承認済みの記録との差 D1〜D9 | 概ね正直 | D7（最後の版の番号を固定するのは AuditMigrationCompatibilityIT の CURRENT_LAST_VERSION = 13 のみ。RoleMigrationCompatibilityIT は版の写しを固定、DatabasePersistenceIT は件数に依らない）を実物で確認。D8 は上流との食い違いとして承認の場へ回している。D9 の扱いは上の Minor |
| 他の単位の枝番・個人に関する値の漏えい | 問題なし | 散文に他の単位の枝番は無い（NFR2.2 などは追跡の所有者の記述にとどまる）。検証前の値は Text・NotText・要求・命令の型の toString で伏せ、PreferencesSecretLeakIT と外部送信を有効にした結合テストで確かめる（SD2・SD4）。現行の PreferencesRequest は氏名だけを伏せるため、5 つ目を足すときの型の更新が設計に書かれている。ログ・トレース・監査に名前を載せない |

### Summary

前の段の Minor 3 件は目標を緩めずに設計で解け、D1 の 1 文化は NFR2.1.2 と機能設計の「保存済みの値を返す」を両立し、未確認の部分は緩める前に依頼者へ諮ると明記されていて、Critical はない。残るのは、追跡の上流の行（NFR5.1・NFR5.3 ほか）の欠落が Major 1 件と、D9 の確かめ先・`updatePreferences` の呼び出し側の洗い出し・構造の検査の取りこぼしの 3 件の Minor で、READY。
