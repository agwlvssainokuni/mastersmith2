# セキュリティの要件 — u1-time-zone-conversion

出典: 機能設計（`construction/u1-time-zone-conversion/functional-design/functional-spec.md`・`rules.md`）、要件（`inception/requirements-analysis/requirements.md` の NFR1〜NFR6）、契約 C1・C4（`inception/contract-design/contract-summary.md`）、質問と答え（`nfr-requirements-questions.md`: Q1: A・Q2: A・Q3: A）、`team.md`（Testing Posture・Code Style）、`project.md`（Forbidden・Mandated）。

U1 は、画面の側の純粋な関数群（種類 library）。通信・保存・ログ・DOM への書き込み・乱数・現在時刻に触れず、認証・認可・個人情報を扱わない。そのため、セキュリティの要件は「入力を信頼せず、決して例外や副作用で壊れない」「値を外へ出さない」「確かめる」の3つに絞る。要件の詳細の ID は、画面・サーバー・メニューを受け持つ他の単位に当たる inception の NFR（NFR1.1〜NFR1.3・NFR3.x・NFR4.x・NFR6.1）を除き、U1 に当たるものを継ぐ（`NFRx.y` に `.n` を足す）。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。NFR2.1（変換で今の性能の目標を変えない）の細目は `tech-stack-decisions.md` の NFR2.1.1〜NFR2.1.3、NFR5.1（日時の必須のテスト）の細目は NFR5.1.1、NFR5.2（テストのタイムゾーンを明示に固定する）の細目は NFR5.2.1・NFR5.2.2。inception の NFR1（セキュリティ）の番号 NFR1.1〜NFR1.3 は他の単位の要件のため、U1 だけに当たる要件は、その次の枝番 NFR1.4（入力と出力と漏えい）・NFR1.5（依存とソースの決まり）から振る（上流の NFR に当たる行が無い追加の要件）。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR5.1.1 | 日時の必須のテスト（`team.md` Testing Posture）のうち、この単位の範囲を単体テストと性質ベースのテストで確かめる: 日付の変わり目（UTC の 15:00 が日本時間の翌日 0:00・月末・年末・うるう日）、夏時間の切り替わりの前後（存在しない時刻・重なる時刻）、分の端数の時差（`+05:45`・`+09:30`・`-03:30`）、秒の端数 0・3・6 桁が落ちない・丸められない、変換して戻すと元の時点に戻る、時差を持たない型は時差を変えても値が変わらない（呼ぶ側が通さないことの確かめは U4） | BR1.1〜BR1.15、`team.md` Testing Posture（業務データの日時のタイムゾーンの変換） | Vitest（単体）と `fast-check`（失敗時の乱数の種を記録して再現できるようにする） |
| NFR1.4.1 | 入力（ローカル時刻の文字・時点の文字・タイムゾーンの名前）は、信頼できない入力として扱う。書式に合わない・範囲外・長すぎる・制御文字を含む入力は、例外を投げず、結果の種別（`invalid`・`unreadable`・`invalid-time-zone`）で返す。正規表現は、指数的な処理時間にならない形（入れ子の量指定子を使わない）にする | BR1.5・BR1.6・BR1.12、`team.md`（信頼できない入力） | 単体テスト: 長い文字（例 10 万文字）・制御文字・`<script>`・`'`・`"`・改行・サロゲートペアを含む入力で、100 ミリ秒以内に結果の種別が返り、例外が出ない。性質ベース（`fast-check`）: 任意の文字列で例外が出ない |
| NFR1.4.2 | 出力の文字（`FormatResult.text`・`ParseResult.instant`・`raw`）は、HTML として解釈されない文字として扱う。U1 は DOM に書き込まず、HTML を作らない。呼ぶ側（U2・U4）は、結果を文字として出し、`dangerouslySetInnerHTML` を使わない | `team.md`（画面に出す値の決まり）、BR1.5（元の文字をそのまま返す） | 単体テスト: `unreadable` の `raw` に `<img onerror>` を入れても、そのまま文字で返る。画面側の確かめは U2・U4 の画面のテスト |
| NFR1.4.3 | 値を外へ出さない。時刻・タイムゾーンの名前・元の文字を、ログ・通信・保存・グローバルな状態・`console` に出さない。例外のメッセージにも入れない（例外を投げないため） | `project.md` Forbidden（ログ・応答に機密を含めない）、BR1.14（純粋な関数） | 単体テスト: `console` の出力を捕まえ、全関数の呼び出しで何も出ないこと。画面の側の依存の検査（lint）: U1 のファイルが通信・保存・DOM の API を import しないこと |
| NFR5.2.1 | タイムゾーンは必ず明示に受け取り、ブラウザの既定のタイムゾーン・既定のロケールを使わない。解釈できない名前は既定へ黙って代えず、`invalid-time-zone` を返す。数字は半角（ラテン数字）で整形する | BR1.12、`team.md`（変換を1か所に閉じ、既定を暗に使わない）、`team.md` Testing Posture（機械の既定のタイムゾーンに結果を預けない） | 単体テスト: テストの JVM でなく画面のテスト（Vitest）の `TZ` を別の値に変えて流しても、同じ結果になる。ロケールが `ja-JP`・`ar-EG` のブラウザでも、数字が半角で整形される |
| NFR5.2.2 | 純粋な関数にする。状態・現在時刻・乱数・グローバルなキャッシュの暗黙の共有に頼らない。`Intl.DateTimeFormat` の使い回し（性能）は、タイムゾーンをキーにした関数の中の閉じたキャッシュとし、外へ出さない（呼び出しの結果が、キャッシュの有無で変わらない） | BR1.14、Q2: A（使い回しの前提） | 単体テスト: 同じ入力を、キャッシュがある・ない状態で呼び、結果が等しい。性質ベース: 同じ入力は同じ結果 |
| NFR1.5.1 | 使う道具の依存は、新しく足さない（`Intl` だけ。Q1: A）。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残す。npm はスクリプトを動かさず、依存の脆弱性検査（OSV-Scanner）の対象に入れる | `team.md`（新しい依存・npm・OSV-Scanner）、Q1: A | `frontend/package.json` と lockfile に、この単位で新しい依存が増えていないこと（コード生成の差分で確かめる） |
| NFR1.5.2 | ソースの先頭に Apache License 2.0 のヘッダー（`/* ... */` の形、年 `2026`、著作権者名 `agwlvssainokuni`）を置く。フォーマット・リンタ・型の検査（`strict`・`any` の禁止・`export default` の禁止・`enum` の禁止）を通す | `project.md` Mandated・`team.md` Code Style | `./gradlew verify` のフロントエンドの検査（Prettier・oxlint・ESLint・tsc・ライセンスヘッダー） |

## 脅威の見立て（U1 の範囲）

| 脅威 | 見立て | 対応 |
|---|---|---|
| 悪意のある長い・異常な文字による処理の停止（ReDoS・メモリ） | U1 は正規表現と文字の走査だけ。入力の長さの上限を、書式の最大の長さ（日時の文字は 26 文字以内など）で先に確かめる | NFR1.4.1（長さを先に確かめ、入れ子の量指定子を使わない） |
| 文字を HTML として描かれて XSS になる | U1 は DOM に触れない。呼ぶ側の責任 | NFR1.4.2（呼ぶ側の決まりを契約に書く） |
| タイムゾーン・時刻の値の漏えい | 値は個人に関する値ではないが、ログに出さない | NFR1.4.3 |
| 既定のタイムゾーンに黙って代わり、誤った時点を保存する | 保存する時点の誤りは、利用者の業務データの誤りになる | NFR5.2.1（`invalid-time-zone` を返す） |

## この単位に当たらない inception の NFR

NFR1.1（自作の SVG の安全）は u6-app-icon-assets、NFR1.2（本人だけが自分の設定を変える）は u2-display-time-zone、NFR1.3（メニューの応答の印）は u5-menu-icons-home、NFR2.2（ホームの API）は u5-menu-icons-home、NFR3.1〜NFR3.3（アクセシビリティ）は画面を持つ単位、NFR4.1・NFR4.2（互換と戻し）は u2・u5、NFR6.1（言語）は画面を持つ単位が受け持つ。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/functional-design/functional-spec.md`・`rules.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A・Q3: A）

## Assumptions & Open Questions

- [assumption] 日時の文字の長さの上限は、時点の文字が最大 35 文字程度（`YYYY-MM-DDTHH:mm:ss.fffffffff+hh:mm:ss`）、ローカル時刻が 26 文字（`YYYY-MM-DD HH:mm:ss.ffffff`）。具体の上限は NFR Design で決める。
- 未解決の点は無い（性能は `tech-stack-decisions.md`）。
