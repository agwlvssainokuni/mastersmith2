# Unit of Work Story Map — auth-audit-foundation

本スコープではユーザーストーリーを作っていないため、要件定義書（`aidlc/spaces/default/intents/260922-auth-audit-base/inception/requirements-analysis/requirements.md`）の機能要件 FR1.1〜FR10.4 を、実現する単位（`unit-of-work.md` の U1〜U4）に割り当てる。

## 要件と単位の対応

| 要件 | 内容（要約） | Unit ID | Directory |
|---|---|---|---|
| FR1.1 | 起動とヘルスチェック | U1 | u1-app-skeleton |
| FR1.2 | 内部DB（H2）と接続設定の切り替え | U1 | u1-app-skeleton |
| FR2.1 | ログイン画面（AppShell の外） | U2 | u2-authentication |
| FR2.2 | ログイン後の画面（AppShell、管理者にだけ「管理」、ログアウト） | U3 | u3-access-control |
| FR2.3 | 日本語・英語の文言とブラウザの言語による切り替え | U1 | u1-app-skeleton |
| FR2.4 | ログイン失敗時の同じ表示 | U2 | u2-authentication |
| FR3.1 | 初期管理者の自動作成 | U2 | u2-authentication |
| FR3.2 | 初期管理者を重複して作らない | U2 | u2-authentication |
| FR3.3 | 設定が無い・不正なときは作らずに起動を続け警告 | U2 | u2-authentication |
| FR3.4 | 初期管理者のパスワードをログに出さない | U2 | u2-authentication |
| FR4.1 | ログインとトークンの受け渡し | U2 | u2-authentication |
| FR4.2 | アクセストークンの有効期限 | U2 | u2-authentication |
| FR4.3 | リフレッシュトークンの有効期限 | U2 | u2-authentication |
| FR4.4 | 有効なアクセストークンの無い要求の拒否（401） | U2 | u2-authentication |
| FR4.5 | ログイン失敗時の同じ応答 | U2 | u2-authentication |
| FR5.1 | トークンの更新とリフレッシュトークンの作り直し | U2 | u2-authentication |
| FR5.2 | 無効・期限切れのリフレッシュトークンの拒否 | U2 | u2-authentication |
| FR5.3 | 再読み込み・新しいタブでのログイン状態の継続 | U2 | u2-authentication |
| FR6.1 | ログアウト（トークンの破棄と無効化） | U2 | u2-authentication |
| FR6.2 | ログアウト後もアクセストークンは有効期限まで使える | U2 | u2-authentication |
| FR7.1 | 連続失敗でのロック | U2 | u2-authentication |
| FR7.2 | ロック中は正しいパスワードでも拒否 | U2 | u2-authentication |
| FR7.3 | 時間経過での自動解除 | U2 | u2-authentication |
| FR7.4 | 成功時に失敗回数を0に戻す | U2 | u2-authentication |
| FR7.5 | しきい値と解除時間の設定 | U2 | u2-authentication |
| FR8.1 | 管理者のみの API・画面のサーバー側の判定 | U3 | u3-access-control |
| FR8.2 | 画面でメニューを隠すことを判定の代わりにしない | U3 | u3-access-control |
| FR9.1 | 対象イベントの記録 | U4 | u4-audit-log |
| FR9.2 | 記録する項目 | U4 | u4-audit-log |
| FR9.3 | 削除の仕組みを作らない | U4 | u4-audit-log |
| FR9.4 | 書き込み失敗時も操作を続けアプリのログに出す | U4 | u4-audit-log |
| FR9.5 | 監査ログをアプリのログで代用しない | U4 | u4-audit-log |
| FR10.1 | 1行1件の JSON のログ | U1 | u1-app-skeleton |
| FR10.2 | ログへのトレースIDの付与 | U1 | u1-app-skeleton |
| FR10.3 | W3C Trace Context による分散トレース | U1 | u1-app-skeleton |
| FR10.4 | OTEL による外部エクスポート（既定は無効） | U1 | u1-app-skeleton |

## 複数の単位にまたがる要件

| 要件 | 主に受け持つ単位 | ほかに関わる単位と内容 |
|---|---|---|
| FR2.2 | U3（管理者にだけ「管理」を表示、管理者向け領域） | U1: AppShell の骨組み。U2: ユーザーメニューのログアウト |
| FR2.3 | U1（文言の切り替えの仕組み） | U2・U3: それぞれの画面の文言を日本語・英語で用意する |
| FR6.1 | U2 | U1: ユーザーメニューを置く場所（アプリシェル） |
| FR9.1、FR9.2 | U4（受け取りと記録） | U2・U3: 出来事の通知と、記録に要る項目（入力されたメールアドレス・失敗の理由・接続元IP・User-Agent・トレースID）を出来事に載せる。U1: トレースIDの仕組み |
| FR10.2 | U1（トレースIDの仕組み） | U2・U3・U4: 各単位のログにトレースIDが付くこと（仕組みを使う側） |

## 単位の中で実現する順番

| Unit | 単位の中の順番 |
|---|---|
| U1 | FR1.1 → FR1.2 → FR10.1 → FR10.2 → FR10.3 → FR10.4 → FR2.3 |
| U2 | FR3.1 → FR3.2 → FR3.3 → FR3.4 → FR4.1 → FR4.2 → FR4.3 → FR4.4 → FR4.5 → FR7.1 → FR7.2 → FR7.3 → FR7.4 → FR7.5 → FR5.1 → FR5.2 → FR6.1 → FR6.2 → FR2.1 → FR2.4 → FR5.3 |
| U3 | FR8.1 → FR8.2 → FR2.2 |
| U4 | FR9.1 → FR9.2 → FR9.5 → FR9.4 → FR9.3 |

単位の中の順番は、先に作るものが後のものの前提になる並べ方（利用者 → ログイン → ロック → 更新・ログアウト → 画面、の順など）である。単位どうしの順番は Delivery Planning で決める。

## 割り当ての確認

- すべての要件（FR1.1〜FR10.4 の36件）が、ちょうど1つの主な単位に割り当てられている。
- すべての単位に要件が割り当てられている（U1: 7件、U2: 21件、U3: 3件、U4: 5件）。
