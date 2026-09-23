# 単位をまたぐ要件の網羅（cross-unit-traceability）

Build and Test の Step 10（stage-level の最終の網羅の関門）。Intent `260922-auth-audit-base` の要件定義書に挙がったすべての `FR`・`NFR` が、いずれかの単位の Code Generation で状態 `OK` として対応づけられ、その対応先のファイルが実在することを確かめる。

## 判定

**合格（pass）。未対応の ID は 0 件。**

| 区分 | 件数 |
|---|---|
| 要件定義書の ID（`FR`・`NFR`） | **56** |
| 群見出し（下位の ID で対応する） | 10 |
| 下位の ID がすべて `OK` | 43 |
| 下位の ID がすべて `OK` だが、一部が後の段で測る（`Unverified`） | 3（NFR1・NFR3・NFR10） |
| **どの単位にも対応づかない（未対応）** | **0** |

## 受け入れ基準（AC）について

本Intentの実行の計画では `user-stories` の段が **SKIP** である（`aidlc-state.md` の INCEPTION PHASE）。したがって `<record>/inception/user-stories/stories.md` は存在せず、**3 つの部分からなる `AC` の ID は1件も無い**。本書の照合の対象は `FR`・`NFR` だけである。

## 照合の方法

Code Generation の `traceability.json` は、要件定義書の `FR`・`NFR` を直接ではなく、**単位ごとの ID**（機能設計の業務の決まり `BR`、単位ごとの `NFR` の枝番）で持っている。そのため次の2段でたどった。

1. 要件の `FR` → 各単位の `construction/<unit>/functional-design/traceability.json`（`FR` → `BR` の対応）
   要件の `NFR` → 各単位の `construction/<unit>/nfr-requirements/traceability.json`（`NFR` → 単位ごとの `NFR` の枝番の対応）
2. そこで得た `BR`／枝番の `NFR` → 各単位の `construction/<unit>/code-generation/traceability.json`（実装とテストのファイルへの対応）

両段とも状態が `OK` で、対応先のファイルが実在するときに、その要件を「対応づいた」とみなす。

対応先のファイルの実在: Code Generation の `OK` の項目 **280 件**が挙げるリポジトリ内のパス **384 件**をすべて確かめ、**欠けは 0 件**だった（そのほか 8 件は、リポジトリ内のパスではなく上流の文書名を本文で参照しているもの）。

状態の読み方。

| 状態 | 意味 | 本書での扱い |
|---|---|---|
| `OK` | 実装とテストがある | 対応づいた |
| `Deferred` | 後の段（`performance-validation`・`observability-setup`・`deployment-execution`）で測る | 対応づいた（実装はある）が、目標の判定は `Unverified`。`build-and-test-summary.md` の表に持ち主を記した |
| `N/A` | 要件が明示的に受け入れた危険、または本Intentの範囲外 | 対応づけの対象外（要件の側にその旨が書かれている） |

## 要件ごとの対応

| ID | 要件の見出し | 受け持つ単位 | 中継する ID | Code Generation での状態 | 判定 |
|---|---|---|---|---|---|
| FR1 | アプリ骨格と内部DB | — | FR1.1, FR1.2 | — | **OK**（群見出し。下位 2 件がすべて OK） |
| FR1.1 | アプリが起動し、ヘルスチェックに応答する。 | U1 | U1: BR1.1, BR1.2 | OK 2 件 | **OK** |
| FR1.2 | 内部DB（ユーザー・監査ログなど、アプリ自身が使うDB）は H2 とする。接続先は ap | U1 | U1: BR2.1, BR2.2 | OK 2 件 | **OK** |
| FR2 | 画面 | — | FR2.1, FR2.2, FR2.3, FR2.4 | — | **OK**（群見出し。下位 4 件がすべて OK） |
| FR2.1 | ログイン画面は、アプリシェル（AppShell）の外に独立したレイアウトとして置き、メー | U2 | U2: BR8.1 | OK 1 件 | **OK** |
| FR2.2 | ログイン後の画面は、make-you-chic-ui の AppShell（サイドバー＋ | U3 | U3: BR5.1, BR5.2, BR5.3 | OK 3 件 | **OK** |
| FR2.3 | 画面の文言は日本語・英語に対応し、ブラウザの言語設定で切り替える。 | U1 | U1: BR6.1, BR6.2, BR6.3 | OK 3 件 | **OK** |
| FR2.4 | ログインに失敗したときの表示は、パスワード誤り・存在しないメールアドレス・ロック中のいず | U2 | U2: BR2.4, BR8.2 | OK 2 件 | **OK** |
| FR3 | 初期管理者の自動作成 | — | FR3.1, FR3.2, FR3.3, FR3.4 | — | **OK**（群見出し。下位 4 件がすべて OK） |
| FR3.1 | 初回起動時に、設定（設定ファイル／環境変数）で指定したメールアドレスとパスワードで、管理 | U2 | U2: BR1.1, BR1.5 | OK 2 件 | **OK** |
| FR3.2 | 2回目以降の起動では、初期管理者を重複して作成しない。 | U2 | U2: BR1.2 | OK 1 件 | **OK** |
| FR3.3 | 初期管理者の設定が無い場合、またはパスワードが規則（12文字以上。NFR2）を満たさない | U2 | U2: BR1.3 | OK 1 件 | **OK** |
| FR3.4 | 初期管理者のパスワードをログに出さない。 | U2 | U2: BR1.4 | OK 1 件 | **OK** |
| FR4 | ログインとトークンの発行・検証 | — | FR4.1, FR4.2, FR4.3, FR4.4, FR4.5 | — | **OK**（群見出し。下位 5 件がすべて OK） |
| FR4.1 | メールアドレスとパスワードでログインする。成功すると、アクセストークンを応答で返し、リフ | U2 | U2: BR2.3, BR5.2 | OK 2 件 | **OK** |
| FR4.2 | アクセストークンの有効期限は ★5分とする。 | U2 | U2: BR4.2 | OK 1 件 | **OK** |
| FR4.3 | リフレッシュトークンの有効期限は ★24時間とする。 | U2 | U2: BR5.3 | OK 1 件 | **OK** |
| FR4.4 | 認証が必要な API は、有効なアクセストークンが無い要求（トークンなし・期限切れ・署名 | U2 | U2: BR4.3, BR4.4 | OK 2 件 | **OK** |
| FR4.5 | ログイン失敗時の応答は、パスワード誤り・存在しないメールアドレス・ロック中のいずれでも同 | U2 | U2: BR2.4, BR2.5, BR2.7 | OK 3 件 | **OK** |
| FR5 | トークンの更新 | — | FR5.1, FR5.2, FR5.3 | — | **OK**（群見出し。下位 3 件がすべて OK） |
| FR5.1 | リフレッシュトークンを使ってトークンを更新すると、新しいアクセストークンと新しいリフレッ | U2 | U2: BR5.4, BR5.6 | OK 2 件 | **OK** |
| FR5.2 | 無効にしたリフレッシュトークン、有効期限を過ぎたリフレッシュトークンによる更新は拒否する | U2 | U2: BR5.3, BR5.5 | OK 2 件 | **OK** |
| FR5.3 | 画面の再読み込みや新しいタブでも、有効なリフレッシュトークンがあればログイン状態が続く。 | U2 | U2: BR8.4 | OK 1 件 | **OK** |
| FR6 | ログアウト | — | FR6.1, FR6.2 | — | **OK**（群見出し。下位 2 件がすべて OK） |
| FR6.1 | ログアウトすると、画面側はアクセストークンを破棄し、サーバー側はリフレッシュトークンを無 | U2 | U2: BR6.1, BR8.6 | OK 2 件 | **OK** |
| FR6.2 | ログアウト後も、発行済みのアクセストークンは有効期限まで使える（決定済みの仕様）。 | U2 | U2: BR4.6 | OK 1 件 | **OK** |
| FR7 | アカウントロック | — | FR7.1, FR7.2, FR7.3, FR7.4, FR7.5 | — | **OK**（群見出し。下位 5 件がすべて OK） |
| FR7.1 | 連続して ★5回ログインに失敗したアカウントをロックする。 | U2 | U2: BR3.1, BR3.2 | OK 2 件 | **OK** |
| FR7.2 | ロック中は、正しいパスワードでもログインを拒否する。表示と応答は通常のログイン失敗と同じ | U2 | U2: BR3.3 | OK 1 件 | **OK** |
| FR7.3 | ロックは ★30分経過で自動的に解除する。 | U2 | U2: BR3.4 | OK 1 件 | **OK** |
| FR7.4 | ログインに成功すると、失敗回数を0に戻す。 | U2 | U2: BR3.6 | OK 1 件 | **OK** |
| FR7.5 | ロックのしきい値（回数）と解除までの時間は application.yml で設定できる | U2 | U2: BR3.7 | OK 1 件 | **OK** |
| FR8 | 管理画面のアクセス制御 | — | FR8.1, FR8.2 | — | **OK**（群見出し。下位 2 件がすべて OK） |
| FR8.1 | 管理者のみが使える API・画面は、サーバー側で権限を確かめる。未認証は 401、管理者 | U3 | U3: BR1.1, BR2.1, BR2.2, BR2.3, BR4.1 | OK 5 件 | **OK** |
| FR8.2 | 画面で管理メニューを隠すことは、サーバー側の権限確認の代わりにしない。 | U3 | U3: BR2.6, BR5.4 | OK 2 件 | **OK** |
| FR9 | 監査ログ | — | FR9.1, FR9.2, FR9.3, FR9.4, FR9.5 | — | **OK**（群見出し。下位 5 件がすべて OK） |
| FR9.1 | 次のイベントを内部DBに記録する: ログイン成功、ログイン失敗、ログアウト、権限不足によ | U4 | U4: BR1.1, BR1.3 | OK 2 件 | **OK** |
| FR9.2 | 記録する項目は、日時・イベントの種類・結果（成功／失敗）・入力されたユーザーID（メール | U4 | U4: BR1.2, BR1.6, BR2.1 | OK 3 件 | **OK** |
| FR9.3 | 本Intentでは監査ログを削除する仕組みを作らない（無期限に保存する）。 | U4 | U4: BR4.1 | OK 1 件 | **OK** |
| FR9.4 | 監査ログの書き込みに失敗した場合は、元の操作を続け、書き込みに失敗したことと記録しようと | U4 | U4: BR3.1 | OK 1 件 | **OK** |
| FR9.5 | 監査ログとアプリのログは別の仕組みとし、監査記録をアプリのログ出力で代用しない（TP）。 | U4 | U4: BR3.2 | OK 1 件 | **OK** |
| FR10 | 構造化ログ・分散トレース・外部エクスポート | — | FR10.1, FR10.2, FR10.3, FR10.4 | — | **OK**（群見出し。下位 4 件がすべて OK） |
| FR10.1 | アプリのログは、1行1件の JSON で標準出力に出す（開発時も同じ）。 | U1 | U1: BR3.1, BR3.2 | OK 2 件 | **OK** |
| FR10.2 | 要求の処理中に出るアプリのログには、その要求のトレースIDを含める。 | U1 | U1: BR3.3 | OK 1 件 | **OK** |
| FR10.3 | 分散トレースを行う。受け取った要求にトレースの情報（W3C Trace Context  | U1 | U1: BR4.1, BR4.2 | OK 2 件 | **OK** |
| FR10.4 | トレースやログの外部エクスポート（OTEL）の機能を持ち、既定は無効、設定で有効にできる | U1 | U1: BR4.4, BR4.5 | OK 2 件 | **OK** |
| NFR1 | 性能 | U1・U2・U3・U4 | U1: NFR1.1, NFR1.2, NFR1.3, NFR1.4, NFR1.5, NFR1.6, NFR1.7, NFR1.8, NFR1.9, NFR1.10, NFR1.11；U2: NFR1.1, NFR1.2, NFR1.3, NFR1.4, NFR1.5, NFR | OK 19 件／Unverified 13 件（NFR1.1, NFR1.2, NFR1.3, NFR1.4, NFR1.6, NFR1.9） | **OK（一部の下位が Unverified）** |
| NFR2 | セキュリティ | U2 | U2: NFR2.1, NFR2.2, NFR2.3 | OK 3 件 | **OK** |
| NFR3 | セキュリティ | U1・U2・U3・U4 | U1: NFR3.1, NFR3.2, NFR3.3, NFR3.4, NFR3.5, NFR3.6, NFR3.7, NFR3.8, NFR3.9, NFR3.10, NFR3.11, NFR3.12, NFR3.13, NFR3.14, NFR3.15；U2: NFR3.1, | OK 33 件／Unverified 1 件（NFR3.3）／受け入れた危険 2 件（NFR3.6, NFR3.7） | **OK（一部の下位が Unverified）** |
| NFR4 | セキュリティ | U2 | U2: NFR4.1, NFR4.2, NFR4.3, NFR4.4 | OK 3 件／受け入れた危険 1 件（NFR4.4） | **OK** |
| NFR5 | セキュリティ | U2 | U2: NFR5.1, NFR5.2, NFR5.3, NFR5.4, NFR5.5, NFR5.6 | OK 5 件／受け入れた危険 1 件（NFR5.6） | **OK** |
| NFR6 | 設定 | U1・U2 | U1: NFR6.1；U2: NFR6.1, NFR6.2, NFR6.3 | OK 4 件 | **OK** |
| NFR7 | 多言語 | U1・U2・U3 | U1: NFR7.1；U2: NFR7.1；U3: NFR7.1 | OK 3 件 | **OK** |
| NFR8 | アクセシビリティ | U1・U2・U3 | U1: NFR8.1；U2: NFR8.1；U3: NFR8.1 | OK 3 件 | **OK** |
| NFR9 | 保守性 | U1・U2・U3・U4 | U1: NFR9.1, NFR9.2, NFR9.3, NFR9.4, NFR9.5；U2: NFR9.1, NFR9.2, NFR9.3, NFR9.4, NFR9.5；U3: NFR9.1, NFR9.2；U4: NFR9.1, NFR9.2 | OK 14 件 | **OK** |
| NFR10 | 観測性 | U1・U2・U3・U4 | U1: NFR10.1, NFR10.2, NFR10.3, NFR10.4, NFR10.5, NFR10.6, NFR10.7, NFR10.8, NFR10.9, NFR10.10, NFR10.11, NFR10.12, NFR10.13, NFR10.14；U2: NF | OK 28 件／Unverified 3 件（NFR10.5, NFR10.7） | **OK（一部の下位が Unverified）** |


## 一部が `Unverified` の3件の内訳

| 要件 | 後の段で測る下位の ID | 持ち主の段 |
|---|---|---|
| NFR1 性能・規模 | U1: NFR1.1・NFR1.3・NFR1.9／U2: NFR1.1〜NFR1.4・NFR1.6／U3: NFR1.1・NFR1.3／U4: NFR1.1・NFR1.2・NFR1.4 | `performance-validation`（U4 NFR1.4 は `observability-setup`） |
| NFR3 秘密情報・安全 | U4: NFR3.3（内部DBのファイルを OS の権限で守る） | `deployment-execution` |
| NFR10 観測性 | U2: NFR10.7／U3: NFR10.5／U4: NFR10.5（いずれも運用で見る指標） | `observability-setup` |

いずれも**実装は存在し、単体・結合・E2E のテストで機能としては確かめられている**。残っているのは、負荷の環境や配備先が決まらないと測れない数値の測定だけである。詳しくは `build-and-test-summary.md` の `## Target Verification Matrix` と `test-results.md` を参照。

## 逆向きの対応（要件に無い実装）

各単位の `traceability.json` の `reverse` に、承認済みの設計に無い追加が記録されている。いずれも `nfr-design` の確定回答または依頼者の決定に基づき、Functional Design を書き換えずに逸脱として記録したものである。

| 単位 | 追加 | 根拠 |
|---|---|---|
| U2 | 問題の種類 `ORIGIN_NOT_ALLOWED` ほか 3 件 | NFR5.4 と `nfr-design/security-design.md` 8章、依頼者の決定 D3-A |
| U3 | 問題の種類 `REQUEST_REJECTED` ほか 7 件 | `nfr-design/security-design.md` 2章（確定回答 Q1）、依頼者の決定 D3-A |
| U4 | `AuditEvent.requestPath` ほか 4 件 | `nfr-design/security-design.md` 1章・6章、依頼者の決定 D2-A |

要件の網羅の判定には影響しない（要件に無いものを足しただけで、要件を落としていない）。

## 承認の場で伝えること

- 未対応の要件は **0 件**。網羅の関門は合格。
- ただし NFR1・NFR3・NFR10 の一部（合計 17 件の下位の ID）は、後の段でしか測れない目標である。承認の場で、これを後の段に引き継ぐことの了解を得る必要がある（`test-results.md` の 5 節）。

## Sources

- `inception/requirements-analysis/requirements.md`（`FR`・`NFR` の一覧。56 件）
- `aidlc-state.md`（`user-stories` が SKIP であること、後の段が EXECUTE であること）
- 各単位の `construction/<unit>/functional-design/traceability.json`（`FR` → `BR`）
- 各単位の `construction/<unit>/nfr-requirements/traceability.json`（`NFR` → 枝番）
- 各単位の `construction/<unit>/code-generation/traceability.json`（実装とテストへの対応、`reverse`）
- リポジトリ内の実ファイル（対応先の実在の確認）

## Assumptions & Open Questions

None.
