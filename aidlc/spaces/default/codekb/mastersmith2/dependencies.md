# 依存関係（mastersmith2）

> 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。所見の本文はこの文書に K-60 を書いた。

## 機能の間の依存（バックエンド）

アーキテクトが `backend/src/main/java/cherry/mastersmith/<機能>/` の `import cherry.mastersmith.<機能>` を検索して数え直した（2026-10-16）。`common` は共通部品、ほかは機能の名前。

| 機能 | 依存する機能（import の先） | 境界テスト |
|---|---|---|
| `auth` | `common`・`user` | `AuthBoundaryArchitectureTest` |
| `access` | `auth`・`common`・`config` | `AccessBoundaryArchitectureTest` |
| `audit` | `access`・`auth`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`（各機能の出来事の型） | `AuditBoundaryArchitectureTest` |
| `user` | `common` | `UserBoundaryArchitectureTest` |
| `useradmin` | `access`・`auth`・`common`・`user` | `UserAdminBoundaryArchitectureTest` |
| `invitation` | `common`・`mail`・`user` | `InvitationBoundaryArchitectureTest` |
| `mail` | なし | `MailBoundaryArchitectureTest` |
| `appearance` | `common` | `AppearanceBoundaryArchitectureTest` |
| `targetdb` | なし | `TargetDbBoundaryArchitectureTest`・`targetdb/testsupport/ExtensionOrderArchitectureTest` |
| `dsl` | なし | `DslBoundaryArchitectureTest` |
| `dslmanage` | `auth`・`common`・`dsl`・`targetdb`・`user` | `DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest` |
| `role` | `common`・`dsl`・`group`・`user` | `RoleBoundaryArchitectureTest` |
| `group` | `common`・`user` | `GroupBoundaryArchitectureTest` |
| `navigation` | `auth`・`common`・`dsl`・`role` | `NavigationBoundaryArchitectureTest` |
| `config` | `common` | 全体の `ArchitectureTest` |

- 依存される側の制限（今回の Intent に効くもの）:
  - `role` に依存してよいのは `audit`（`role.domain`）と `navigation`（`role.service`・`role.domain`）だけ（`RoleBoundaryArchitectureTest` 129 行。K-50、本文は `architecture.md`）。
  - どの機能も `navigation` に依存しない（`NavigationBoundaryArchitectureTest`）。
  - `targetdb` の外から `targetdb.config`・`targetdb.repository` に依存しない。`targetdb` は `dsl`・`dslmanage`・`web` を知らない（K-49、本文は `architecture.md`）。
  - `dsl` の境界テストは「dsl は targetdb を知らない」を持つ。業務データの機能が `dsl` の提供口 `ActiveDslModelProvider` に依存することを止める規則があるかは、今回読んでいない（K-51 の見立て）。
- 逆向きの知らせは Spring の出来事（監査は `audit` が各機能の `domain` の出来事の型を import して受ける。K-54）。

## 画面の依存

- 機能（`frontend/src/features/<featureId>/`、13 機能）どうしの import は `frontend/eslint.config.js` の `no-restricted-imports` で止める。`src/shared/` から `src/app/`・`src/features/` を読むことも止める（2つの規則を一部だけ読んだ）。
- 画面は make-you-chic-ui を npm の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で使う（`frontend/package.json`）。

## ビルドの依存

- `backend` の WAR は `frontend` のビルド結果（`dist`）を同梱する。
- `frontend` は `vendor/make-you-chic-ui` に、`backend` は `vendor/java-mustache-processor`（Gradle の composite build、`cherry-mustache` 0.1.0）に依存する。
- 依存の取得元は Maven Central だけに固定（`settings.gradle.kts`。今回は開いていない。`team.md` の記録）。lockfile は `backend/gradle.lockfile`・`frontend/package-lock.json`。

## 外部の依存（主なもの）

版は `technology-stack.md`。依存の脆弱性の関門は `./gradlew verify` の中の OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む）で、更新の知らせは Dependabot（`.github/dependabot.yml`）。

## サブモジュールと見送りの依存

| 依存 | 今の固定 | メモ |
|---|---|---|
| `vendor/make-you-chic-ui` | `a9309c4`（`git submodule status` の `heads/main-19-ga9309c4`） | 次の候補 `b0a1e79` は手元の履歴にある（K-60） |
| `vendor/java-mustache-processor` | `8d44c36`（0.1.0） | 今回の Intent では触れない見込み |
| `axe-core`（npm、dev） | `^4.13.0`、lockfile は 4.13.0 の1つ | `>= 4.14.0` を Dependabot で見送り（K-60） |

**K-60 make-you-chic-ui の固定先の更新と axe-core の取り込みの前提**

- 確かめた事実:
  - サブモジュールは `a9309c4`。`b0a1e79`（「Dropdown: 説明付き項目でlabel-content-name-mismatch(WCAG 2.5.3)に 違反しないようDOM構造を変更」、2026-10-16）は手元の履歴にあり、`a9309c4` の次の1コミット（`git log a9309c4..b0a1e79`）。変わるのは `Dropdown.tsx`・`Dropdown.css`・`Dropdown.test.tsx` の3つだけ（119 行の追加・42 行の削除、`git diff --stat`。アーキテクトが確かめ直した）。
  - `.github/dependabot.yml` の npm の項で `axe-core` の `versions: [">= 4.14.0"]` を `ignore` し、理由と外す時期（画面の側の名前の直しと make-you-chic-ui の Dropdown の直しを取り込んだ後）をコメントに書いている（65〜75 行）。
  - E2E の axe（`frontend/e2e/support/axe.ts`）はタグ `wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa` で流す。`label-content-name-mismatch` のタグは `wcag21a`・`wcag253` と `experimental`（手元の 4.13.0 の規則の定義、`frontend/node_modules/axe-core/axe.js` 32991 行。入れた依存の読み取り）。4.14 で `experimental` が外れると、今のタグの指定のまま流れる。既知の違反の一覧（`KNOWN_VIOLATIONS`）は空で、どの違反も失敗にする。
  - 画面の部品のテストの `vitest-axe` も同じ `axe-core`（lockfile の 4.13.0 の1つ）を使う。
- 今回の Intent への意味: 取り込みの順は「固定先の更新（承認を得た専用のコミット、fast-forward の例外。`project.md` の Mandated と `team.md` の Way of Working）→ 行のトリガーの名前の直し（K-55）→ axe-core を 4.14 以降に上げて `ignore` を外す → E2E（080・100・120・130・160）で確かめる」。K-56 が正しければ、`b0a1e79` だけでは 080 などの違反が残りうる。`vitest-axe` で流れる規則も変わりうるため、画面の部品のテストも確かめの範囲に入る。
- 見立て（未検証）: 4.14 の `axe.js` は手元に無く、規則の判定の変わり方は確かめていない（K-55）。

## Sources

- アーキテクトの import の検索（2026-10-16）と境界テストのファイルの一覧（`git ls-files`）。
- 開発担当の調査の K-60 と Frameworks & Libraries。
