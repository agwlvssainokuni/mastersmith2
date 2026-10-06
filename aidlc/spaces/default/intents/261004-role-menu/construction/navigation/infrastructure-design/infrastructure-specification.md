# 基盤の仕様 — U5 navigation

U5 navigation は service の単位で、B7 で作ります。読み取りだけの2本の API を持ちます（契約 C9）。

- 業務のメニュー: `GET /api/me/navigation`
- テーブルの置き場の問い合わせ: `GET /api/me/table-access?schema=…&table=…`

既存のアプリのコンテナと組み込みの H2 をそのまま使い、足すのは同梱するアイコンの一覧のファイル1つだけです。表・移行・設定・秘密は足しません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・検証環境は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/navigation/nfr-design/` の7つ: `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`
  - `construction/navigation/nfr-requirements/`（`reliability-requirements.md`・`tech-stack-decisions.md`）
  - `construction/navigation/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`（Navigation）
  - `inception/contract-design/contract-summary.md`（C1・C3・C5・C9）
  - `inception/delivery-planning/bolt-plan.md`（B7）
  - group・role の基盤の設計の成果物（読み取りだけ）
- 既存のもの（正とする。読むだけ）: `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`backend/src/main/resources/application.yaml`

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のアプリのコンテナ1つ。変えない | 既存のアプリの中の読み取りの機能 |
| Instances | 単一のインスタンス | 組み込みの H2。写しは要求をまたいで持たないため、利用者の数に比例するメモリは増えない（`scalability-design.md` 3節） |
| Networking | 既存のまま（8080、同じオリジン） | 外への呼び出しは無い |
| Storage | 変えない（表を足さない） | `tech-stack-decisions.md`（表・移行は足さない） |
| Environments | 配備は `compose.yaml`、負荷の試験は使い捨ての環境（`docker/perf/compose.yaml`）、CI はテストだけ | `team.md` の Deployment |
| IaC approach | 作らない | 配備先が決まっていない |
| Resource sizing | 既存のまま | 1要求で同時に持つ接続は1本（`scalability-design.md` 2節）。メニューの見積もりは毎秒 10 要求ほど |
| Configuration | `application.yaml`・`.env.example` は変えない。新しい環境変数・秘密は無い | — |
| Bundled resource | `backend/src/main/resources/navigation/allowed-icons.txt`（18 個、`#` の行と空行は読み飛ばす）。WAR の `WEB-INF/classes/navigation/` に入る | 起動時に1回読み、誤りなら起動を止める（`reliability-design.md` 4節） |
| Schema migration | 無い | 読み取りだけ（`reliability-requirements.md`） |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。前の版には2本の API が無い。版 2 の DSL を読めず DSL が無い状態で起動するため、業務のメニューは出ない | 移行が無い。DSL の扱いは dsl-v2 と U3・U4 の配備の段への引き継ぎにまとめる（4節） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2） | database（読み取りだけ） | 変えない | role の解決の口の読み取りの間だけ接続を使う。H2 の読み取りは MVCC で行の排他を待たない（`reliability-design.md` 3節） |
| 接続プール（HikariCP） | database（接続） | 変えない | 1要求で同時に持つ接続は1本。上限を下げた k6 の場面は置かず、結合テスト `NavigationConnectionUsageIT` で確かめる |
| DSL の写し（`ActiveDslModelProvider`） | 既存の部品（メモリ） | 変えない | 適用し直しとの重なりで権限の無い項目を返さない（`reliability-design.md` 2節） |
| 権限の解決の口（`EffectivePermissionResolver`） | U4 の部品 | 変えない | メニューは `snapshotFor` を1回、置き場は `resolve` を1回（`performance-design.md` 1節） |
| Tomcat | web サーバー | 変えない（B1 で足す `max-swallow-size` は navigation の GET に関わらない） | エンコードしない文字・8 KiB を超える問い合わせは Tomcat の HTML の 400（受け入れた制約、`security-design.md` 7節） |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | キャッシュは置かない（BR1.4） |

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| アイコンの一覧のファイル `navigation/allowed-icons.txt` | U5 navigation | U7 app-frame-ui（画面の `app/registry` の一覧） | サーバーは起動時に読む。画面のテスト `frontend/src/app/registry/allowedNavIcons.test.ts` が `frontend/` の外の相対の道で読み、集まりの一致を `verify` の段 5 で確かめる（NFR6.7） |
| 権限の解決の口（C5） | U4 role | U5 navigation | 読み取りだけ。口の値の網羅は role の `EffectivePermissionConsistencyIT`。navigation は `role.service`・`role.domain` だけに依存する（`NavigationBoundaryArchitectureTest`） |
| DSL の写しの提供口（C3） | U2 dsl-v2 | U5 navigation | `dsl.service`・`dsl.domain` だけに依存する |
| 負荷の試験の準備の台本（`perf/`） | U4 role（B6 で作る。role の Q2 A） | U5 navigation（B7 で navigation の分を足す。この段の Q1 A） | `cicd-pipeline.md` 5節 |
| 手元の監視のダッシュボードの区画（この Intent の API） | Observability Setup（group の Q1 A） | U3・U4・U5 | `monitoring-design.md` 5節 |

## 4. 配備の段への引き継ぎ

- navigation 自身の移行と設定の変更は無く、戻しはイメージだけです。
- 戻した版では、版 2 の DSL を読めず DSL が無い状態で起動します（dsl-v2 の `logical-components.md` 7節）。そのため、業務のメニューは出ません。手順は dsl-v2 と U3・U4 の配備の段への引き継ぎ（戻す前のプレビューの破棄、戻した後の版 1 の DSL の生成し直し）にまとめて扱います。
- 配備の後のスモークテストでメニューを確かめるなら、その前に版 2 の DSL の適用と、ロール・作業ロールの用意が要ります。これらは監査に残る操作のため、前もって依頼者に伝えます。

## 5. 上流との差

この文書の範囲（配備・内部DB・同梱のファイル・共有の資源）では、承認済みの NFR 設計と違う作りはありません。負荷の試験のデータの入れ方の差は `cicd-pipeline.md` 10節に、監視の式の名前の差は `monitoring-design.md` 7節に書きます。
