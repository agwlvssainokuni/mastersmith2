# Intent Statement — auth-audit-foundation

## Problem Statement

MasterSmithの他の全機能（メニュー・ナビゲーション、一覧画面、詳細・編集画面などIntent I/J/K以降）は、認証・認可・監査ログの基盤の上に成り立つが、この基盤はまだ存在しない。本Intentは、その基盤（認証・認可基盤＋監査ログ・構造化ログ基盤）を最初に確立する [Q1]。

## Target Customer

MasterSmithを導入・運用する組織の内部管理者・利用者であり、外部エンドユーザー向けではない [Q3]。

## Success Metrics

- ログイン・JWT発行/検証・管理者権限ゲートが動作すること [Q2]
- 対象イベントが漏れなく監査ログに記録されること [Q2]
- 後続Intent（D以降）がこの基盤の上に問題なく構築できること [Q2]

## Initiative Trigger

新規プロジェクト（MasterSmith）の立ち上げそのものであり、他の全機能の前提となる基盤が必要なため [Q4]。

## Initial Scope Signal

- ワークフロー選択スコープ: `auth-audit-foundation`（カスタム合成スコープ、22/33ステージ実行）[scope]
- ユーザー確認済みプロダクト境界: 上記スコープはユーザーが意図するプロダクト境界と一致している [Q8]

## Assumptions & Open Questions

None.
