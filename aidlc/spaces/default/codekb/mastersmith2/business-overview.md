# 業務の概要（mastersmith2）

## 目的

mastersmith2 は、管理画面の土台となるアプリケーションである。提供するのは、認証（ログイン・トークンの更新・ログアウト・アカウントのロック）、管理者の API の認可、そしてそれらの出来事を内部DBへ追記する監査ログである。業務データの CRUD は後続の Intent で扱う予定であり、まだ無い。配備先は当面、開発者の PC 上のコンテナ（colima）だけである。

## 利用者と主な機能

| 利用者 | 機能 | 主な実装 |
|---|---|---|
| 管理画面の利用者 | メールアドレスとパスワードでログインし、アクセストークンとリフレッシュトークン（Cookie）を受け取る | `LoginService`・`AuthController` |
| 管理画面の利用者 | リフレッシュトークンでアクセストークンを更新する | `TokenRefreshService` |
| 管理画面の利用者 | ログアウトする（画面側でトークンを破棄し、サーバー側でリフレッシュトークンを無効にする） | `LogoutService` |
| 管理者 | 管理者の API に入れるかを確かめる（未認証 401／管理者でない 403／管理者 200） | `AdminCheckController` ほか `access` |
| 運用者・監査者 | 認証の出来事（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`）と管理者の API へのアクセス拒否（`ACCESS_DENIED`）を、追記だけの監査ログとして内部DBに残す | `AuditEventListener`・`AuditEventRecorder` |
| 運用者 | 起動時に初期管理者を1回だけ作る | `InitialAdminInitializer` |
| 開発者（運用者を兼ねる） | コンテナで起動・停止し、手元の監視と使い捨ての環境での負荷の試験で状態を確かめる | `compose.yaml`・`docker/perf/compose.yaml`・`perf/`・`docker/monitoring/` |

## 業務上の決まり（監査に関わるもの。前の Intent の記録を引き継ぐ）

- 監査ログは内部DB（組み込みの H2）の `audit_events` 表に追記するだけで、変更・削除の処理は持たない。
- 監査の記録は、元の操作の確定の後に行う。元の操作が取り消されたら記録しない。
- 監査の書き込みに失敗しても、元の操作（ログインなど）は失敗させない。失敗は ERROR のログ1件で知らせ、再試行しない（BR3.1）。
- 監査の行のトレースIDは、要求のトレースIDと一致させる。

## 業務上の問題の経緯

| 見つかったこと | 内容 | 状態 |
|---|---|---|
| F2 | 同時 10 件の成功のログインでコネクションプールが尽き、監査の行が欠ける | Intent `260923-audit-pool-exhaustion` でプールの上限の既定を 30 に上げて対応済み。上限に達すると再び起きうる危険は既知の制約として README に記録 |
| F3 | 高い負荷（トークンの更新を毎秒約 3,000 件）で、アプリのコンテナがメモリの上限 1GB で止まる（OOMKilled） | **本 Intent の対象** |
| F4 | colima の VM の CPU 2 では、同時 10 件のログインの p95 が約 1.6 秒で、目標の 1 秒を超える | **本 Intent の対象** |

F3・F4 はどちらも、colima の VM の大きさ・コンテナの上限・JVM の設定の組み合わせで決まる。仕組みは `architecture.md` の「実行環境の資源の構成」と「Interaction Diagrams」に、所見と懸念は `code-quality-assessment.md` の TD-6〜TD-11 に記す。

## 対象の範囲の外

画面（`frontend/`）とデザインシステム（`vendor/make-you-chic-ui/`）は流し読みだけである。今回深く読んだ範囲は `reverse-engineering-timestamp.md` の Scope of Analysis を参照。
