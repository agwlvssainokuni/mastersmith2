# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、管理画面の土台となるアプリケーションである。現時点（コミット `6afbf97`）で提供するのは、認証（ログイン・トークンの更新・ログアウト・アカウントのロック）、管理者の API の認可、そしてそれらの出来事を内部DBへ追記する監査ログである。業務データの CRUD は後続の Intent で扱う予定であり、まだ無い。

## 利用者と主な機能

| 利用者 | 機能 | 主な実装 |
|---|---|---|
| 管理画面の利用者 | メールアドレスとパスワードでログインし、アクセストークンとリフレッシュトークン（Cookie）を受け取る | `LoginService`・`AuthController` |
| 管理画面の利用者 | リフレッシュトークンでアクセストークンを更新する | `TokenRefreshService` |
| 管理画面の利用者 | ログアウトする（画面側でトークンを破棄し、サーバー側でリフレッシュトークンを無効にする） | `LogoutService` |
| 管理者 | 管理者の API に入れるかを確かめる（未認証 401／管理者でない 403／管理者 200） | `AdminCheckController` ほか `access` |
| 運用者・監査者 | 認証の出来事（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`）と管理者の API へのアクセス拒否（`ACCESS_DENIED`）を、追記だけの監査ログとして内部DBに残す | `AuditEventListener`・`AuditEventRecorder` |
| 運用者 | 起動時に初期管理者を1回だけ作る | `InitialAdminInitializer` |

## 業務上の決まり（本 Intent に関わるもの）

- 監査ログは内部DB（組み込みの H2）の `audit_events` 表に追記するだけで、変更・削除の処理は持たない。
- 監査の記録は、元の操作の確定の後に行う。元の操作が取り消されたら記録しない。
- 監査の書き込みに失敗しても、元の操作（ログインなど）は失敗させない。失敗は ERROR のログ1件で知らせ、再試行しない（前の Intent の BR3.1）。
- 監査の行のトレースIDは、要求のトレースIDと一致させる。

## 現在の業務上の問題（本 Intent の対象）

前の Intent の負荷の試験（見つかったこと F2）で、同時 10 件の成功のログインでコネクションプールが尽き、10 件すべての監査の書き込みが失敗することが確かめられた。ログインの応答は 200 のままだが約 7.2 秒かかり、監査ログから `LOGIN_SUCCEEDED` の行が欠ける。原因と関係する既存の決まりは `architecture.md` の「対話の図」と `code-quality-assessment.md` の技術的な負債に記す。

## 対象の範囲の外

画面（`frontend/`）とデザインシステム（`vendor/make-you-chic-ui/`）は、今回は流し読みだけである（`reverse-engineering-timestamp.md` の Scope of Analysis を参照）。
