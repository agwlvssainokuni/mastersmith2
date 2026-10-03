# Code Generation の段の承認の場の決定（Intent 260930-user-admin）

承認の場の前に依頼者が決めたことを、承認の操作の理由だけでなくこの成果物に残す（`project.md` の学び）。単位ごとの記録（`construction/<unit>/code-generation/`）はレビューの後のため書き換えず、差と持ち越しはこの文書にまとめる。

## 1. 段の結果

| Bolt | 単位 | `develop` への統合 | 統合の後の CI |
|---|---|---|---|
| B1 | U1 利用停止 | squash `ded2653` | success |
| B2 | U2 ページ送りの共通化・U4 403 の画面 | `9b42c2d`・`1de9ef6`（単位ごとの squash） | success |
| B3 | U3 管理の API（前半） | squash `f6bf385` | success（9分21秒） |
| B4 | U3 管理の API（後半） | squash `4bd600d` | success（12分29秒、run 37036697597） |
| B5 | U5 利用者の管理の画面 | fast-forward（`17af97d`〜`afd69c3`、記録 R4 `335aba6`） | success（約12分、run 37080444724） |

B5 の統合の前の関門（`afd69c3` の前の `81d2423`、C2′ の前）: clean 付きの `verify` が通過（バックエンド 単体 1506・結合 689、行 98.90%・分岐 94.83%、画面 109 ファイル・955 件、行 97.42%・分岐 92.85%）、`osvScan --rerun-tasks` で失敗の条件 0・警告 16（npm の開発用）、E2E 13 ファイル・152 件がすべて expected。詳しくは `construction/u5-user-admin-ui/code-generation/generation-notes.md` の「Step 22（C2′ の前）」。

## 2. 段全体のレビュー（stage、1回目、READY）の扱い

| 指摘 | 扱い（依頼者の決定: 推奨どおり） |
|---|---|
| R-01 Major（N-19 を統合。AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 が traceability.json で OK のまま） | 受け入れて記録する。4つの AC は「実際のブラウザでは閉じた後のフォーカスが行の『操作』に戻らない（N-19）。後の Intent で解く」条件つきとして 4節の一覧に載せる。目印のテストは足さない（統合済み） |
| R-02 Major（AC3.2.9・AC3.2.10・AC2.2.6 をどの単位も OK にしていない） | Build and Test の網羅の確認で、`UserAdminListApiIT`・`UserAdminOperationsApiIT`・E2E 110 の順7・順9 を根拠に判定する。承認済みの traceability.json は書き換えない |
| R-03 Minor（各単位の code-summary.md に、レビューの後の変更と関門の結果が反映されていない） | Build and Test の最初に、最新の実測（上の1節の値と CI）を1か所にまとめる |
| R-04 Minor（403 の文言の差が mockups.md の 9節にしか無い） | 3節の差の一覧に載せる |
| R-05 Minor（持ち越しが単位ごとに散らばる） | 4節の1つの一覧にまとめる |

## 3. 段全体の、承認済みの文書との差（単位の記録に書いたものの要約と、そこに無いもの）

- 403 の文言: FR2.3・AC2.1.1・AC2.2.1 の「管理者ではなくなった旨」は画面に出ず、S6 は「この画面を使う権限がありません」とだけ出す（refined-mockups の RQ6 C。`inception/refined-mockups/mockups.md` 9節）。U4・U5 の実装はこの決定のとおり。
- make-you-chic-ui の固定先: 計画の `3481488` ではなく `3d9521a` に上げた（N-3・N-4 の直しを make-you-chic-ui の側で取り込んだ。依頼者の決定）。
- B5 の統合: N-19 の直し（C2′）を待たずに統合した（依頼者の決定）。
- U5 の計画との差のうち、承認の場で受け入れたもの: N-10・N-11（`useUserAdmin` の受け方と部品の props の小さな違い）、N-12〜N-14・N-21〜N-23（テストの作り方の記録）、N-16・N-17（報告の部品が `.ts` の拡張子つきで読み込む・`E2E_TRACE` の誤りの知らせ）、README の 090 の節の1行。いずれも `construction/u5-user-admin-ui/code-generation/generation-notes.md` に詳しい。
- U5 の分岐の目安: `useUserAdmin.ts` 85.06%・`UserRowActions.tsx` 83.33%・`lockedUntil.ts` 87.5%・`profileInput.ts` 75.0% が記録のための目安 90% を下回る。下限（行 80%・分岐 70%）は満たすため受け入れる。
- そのほかの単位ごとの差は、各単位の `code-summary.md`・`generation-notes.md` のとおり（U3 の G-23 `invitation.lock` の置き場など）。

## 4. 後の Intent への持ち越し（1つの一覧）

| 項目 | 中身 | 先に書く再現のテスト・確かめ | 出どころ |
|---|---|---|---|
| N-19 閉じた後のフォーカス | make-you-chic-ui の `useFocusTrap` が、背景に `inert` が付いたまま前の要素へフォーカスを戻そうとし、body に移る。AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7 は条件つき。招待の画面など既存の画面でも起きている見込み（未確認） | make-you-chic-ui の直った版へ固定先を上げる専用のコミット（C2′）。E2E 110 の `confirmAction`・120 の `expectBackgroundInteractive` を「閉じた後にフォーカスが行の『操作』に戻る」ことを確かめる形に替える。流し直す手順は U5 の generation-notes.md の「C2′ の後に流し直すもの」 | U5 N-19、単位のレビュー R-01・R-02、段全体のレビュー R-01、`make-you-chic-ui-request-3.md` |
| Q-H 一意の制約の違反の例外の文 | H2 の 23505 の例外の文に重なった値（メールアドレスなど）が入りうり、repository の外へ出ると TRACE の `TraceAspect` が出しうる（未検証） | 一意の制約に当たる要求を TRACE と INFO で送り、出力に重なった値が無いこと | U3 計画 Q-H、U3 レビュー R-04 |
| 出力を捕まえるテストの範囲の弱さ | `CapturedOutput` が別の文脈の背景のスレッドの出力も含む。B4 の関門で `MailConfigurationIT` が1回落ちた（不安定と確かめられていない扱い） | 文脈の分離か、捕まえる範囲を自分のスレッドに絞る形。CI で同じテストが落ちたら二度目として原因を直すまで進まない | U3 code-summary.md 9節 |
| U3 監査の組み立ての失敗のログ | `AuditEventListener` の組み立てに失敗したとき、`fields(UserAdminAuditEvent)` が `auditEventType` のキーに操作の区分（例 `SUSPEND`）を載せる。本番では起きない経路 | 組み立ての失敗を起こしたときのログのキーと値を確かめるテスト | U3 code-summary.md 8節（承認の場の決定: 後の Intent へ） |
| V9 の後の戻し | V9（`users.suspended`）の後に1つ前の版のアプリへ戻すと、利用停止が効かない | 戻しの前に停止中の利用者を確かめる手順（deployment-pipeline で決める） | U1 code-summary.md、README の戻しの節 |
| 8KB を超える要求の HTML の 400 | 検索の文字は 254 字で止めるので届かない見込み。届いたときは一般の失敗として扱う（テスト済み） | 残る危険として受け入れ（U5 計画 Q-C A）。URL の長さを数える作りは足さない | U2 申し送り、U5 Q-C |
| U3 R-02 操作の前の確かめ直しの隙 | 再開・失敗回数を戻す操作の、操作した人の確かめ直しを排他なしで行う隙を受け入れた | — | U3 レビュー R-02 |
| `ms-pool-pending` の式の見直し | NFR5.10 の申し送り | — | 配備先が決まったとき |

## 5. Build and Test に引き継ぐこと

- R-02（網羅の記録の欠け3つ）と R-03（最新の実測を1か所へ）。
- 各単位の `code-summary.md` の「Build and Test に引き継ぐこと」の表（U3 の 9節など）。
- 性能・観測・配備の持ち主の段へ回したもの（NFR5.x の Unverified、スモークテスト、戻しの条件）は各単位の記録のとおり。

## 6. 承認の場を開くまでの経過（f82f186 の後、2026-10-03）

- 承認の場を開く操作が「この段のコードの作業が見えない（REQUIRED_SOURCE_WORK_MISSING）」で断られた。理由は、`develop` の先頭に記録だけのコミット（R2・R3・R4・f82f186）が続き、`aidlc/` の外のコードが最新のコミットにも未コミットの変更にも無かったため。段のレビュー（2回目）は許されず、Request Changes は「recovery-question choice was not Request Changes」で5回断られた（原因は分かっていない。製品の不具合として報告）。
- 依頼者の決定「U3を直す」で、4節の持ち越しの「U3 監査の組み立ての失敗のログ」を後の Intent へ回さず、いま直した（短命のブランチ `fix/260930-user-admin-audit-fields` から squash で `b126bdc`。再現のテスト2件。clean 付きの verify（単体 1508・結合 689、行 98.9%・分岐 94.8%）と osvScan（失敗 0・警告 16）を通した）。4節の表の該当の行は、これで解いた。
- 続けて、後の Bolt で自分のファイルが変わった U1・U4 と、どの単位の一覧にも載っていないパスがあるとして断られた。U1・U4 の2回目のレビュー（READY）、U3 の一覧に `.gitleaks.toml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`（B1 の前の Jackson 3.1.7 と Gitleaks の除外、f299400）を足して2回目のレビュー（READY）、U5 の一覧に移した元の `fieldErrors` の2つと make-you-chic-ui の中の4つを足して2回目のレビュー（READY）を行った。
- 残った4つ（`frontend/playwright-report/index.html`・`frontend/test-results/.last-run.json`・`frontend/test-results/e2e-results.json`・`.idea/workspace.xml`）は、段の始めからあった git の対象外のファイル（前の Intent の E2E の報告を決まりどおり消したもの・IDE が書き換えたもの）で、一覧に載せられず（git の対象外は記録に使えない）、戻しもできない。依頼者の明示の承認を得て、承認の場を開く操作1回にだけ `AIDLC_SKIP_SOURCE_FRESHNESS=1` を付けた。ほかに一覧に載らない変更が無いことは、その直前の断りの一覧（この4つだけ）で確かめた。
- 2回目のレビューの Minor（記録の件数・経緯の追記、U5 の言語の欄の送信中の防ぎ、project.md の Jackson 3.1.6 の記述）は、Build and Test と学びで扱う。
- 記録の欠け: B5 の squash の手順（`git restore --source=HEAD -- aidlc/`）で、f82f186 の後に監査ログ `audit/sakura-local-4e42a93f87ce.md` に追記されていた分（承認の場の断り・Request Changes の断り・そのときの問いと答えの記録）を消してしまい、戻せなかった。この節の内容が、その間に起きたことの記録の代わりである。
