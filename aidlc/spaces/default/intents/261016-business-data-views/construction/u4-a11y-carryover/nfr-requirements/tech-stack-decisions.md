# 技術の選択 — U4 u4-a11y-carryover

U4 は新しい依存を足さない。決めるのは、axe-core の版の選び方と、そのライセンスの扱いだけ。

## 上流の ID との対応

| 上流 | この文書で扱うこと | 要件の ID（`security-requirements.md`） |
|---|---|---|
| NFR4 セキュリティ（依存） | axe-core の取り込みの確かめ | NFR4.8・NFR4.9 |
| NFR5 アクセシビリティ | axe-core の版と、版が1つにそろうこと | NFR5.1・NFR5.2 |
| NFR7 テスト | 道具を変えないこと | NFR7.4・NFR7.7 |

## 選択

| 道具 | 今 | 決め | 理由 |
|---|---|---|---|
| axe-core（devDependency） | 4.13.0（`frontend/package.json` は `^4.13.0`） | B7 の時点で公開されている 4 系の最新の版（4.14.0 以上）にする（Q1: A） | 下の「axe-core の版の選び方」を参照 |
| vitest-axe | 0.1.0（MIT） | 変えない | axe-core を `^4.4.2` で求め、frontend の axe-core を共有する。新しい版で動くことを `./gradlew verify` で確かめる |
| make-you-chic-ui | `a9309c4` | `b0a1e79` に上げる（B3）。依頼（5）が公開されていれば B7 で再び上げる | lockfile に差が無く、`hasInstallScript` も増えない（機能設計の 2節）。頭文字の関数 `getInitials` を公開の口から使う |
| fast-check・Playwright・Vitest・Testing Library | 今の版 | 変えない | 性質のテストと E2E は今の道具で書ける |

### axe-core の版の選び方

- **範囲と固定**: `package.json` の範囲は `^<採った版>` とし、lockfile で1つに固定する。
- **B3 の試し**: その時点の最新の版で行い、版を記録する。B7 で版が変わっていても、B7 で E2E の全件を流し直して判定する。
- **上げる前の確かめ**: 変更の記録（CHANGELOG）を読み取りで確かめ、新しく既定で流れる規則（`experimental` の印が外れたもの）を記録する。
- **最新を選ぶ理由**: 4.14 に留めると、すぐ次の知らせで同じ検査をやり直すことになるため。

## 版を1つにそろえる範囲（Q3: A）

- **確かめる範囲**: `frontend/package-lock.json` だけ。`frontend` で `npm ls axe-core` を流し、vitest-axe の分を含めて1つであることを記録する。
- **vendor の lockfile**: `vendor/make-you-chic-ui/package-lock.json` にも開発用の axe-core 4.13.0（MPL-2.0）があるが、版を記録するだけにする。理由は次の2つ。
  - make-you-chic-ui のテスト用で、このリポジトリの CI・E2E・成果物に入らない。
  - このリポジトリから変えられない（project.md の Forbidden）。

## ライセンス（team.md の「採用の前にライセンスを確かめる」）

axe-core のライセンスは MPL-2.0 で、Apache License 2.0 と異なる（`frontend/package-lock.json` と `frontend/node_modules/axe-core/package.json` の `license`）。今までの記録に採用の理由が無かったため、ここに残す。

採用の理由:

- 開発のときだけ使う devDependency である。
- 検査の中でだけ読む。E2E は Node の側で `axe.min.js` を読み、`page.evaluate` で評価する（`frontend/e2e/support/axe.ts` 16〜24行）。画面の部品のテストは vitest-axe から読む。
- 成果物（`frontend/dist` と WAR）に同梱しない（NFR4.8 で確かめる）。
- このリポジトリは axe-core のソースを変えない。

確かめること: B3 の試しと B7 で、新しい版の lockfile の `license` が MPL-2.0 のままであること。変わっていたら、取り込む前に依頼者に諮る。

## 取り込みの確かめ（NFR4.8）

- `ignore-scripts=true`（`frontend/.npmrc`）のまま入れる。新しい版の lockfile の項に `hasInstallScript` が無いことを確かめる。
- OSV-Scanner の結果を記録する。High は警告（devDependency で、成果物を作る道具の一覧 `config/npm-build-tools.txt` に無い）。`MAL-` は止める。
- B7 で `.github/dependabot.yml` の axe-core の `ignore` とそのコメントを外す。持ち越すときは、理由と外す時期を書き直す（FR14.6）。

## 上流との差

- **違反の要素の見立て**: 機能設計の 2節の見立て（トップバーが主な原因）と、前の Intent の記録（Dropdown のトリガーと作業ロールのメニューの項目）が食い違う。技術の選択には響かない。扱いは `security-requirements.md` の「上流との差」の1と NFR5.4 に書いた。
- **機能設計の承認の場の決定**: `nullable` を U2 が持つこと、`lookup` を外すこと、U3 で欠けた値を形の崩れとして扱うこと。どれも U4 には当たらない。

## 後の段に回すこと

- 採る axe-core の版と、CHANGELOG で確かめた規則は、B3（試しの版）と B7（採る版）のコード生成の記録に書く。
- 依頼（5）の取り込みで make-you-chic-ui を再び上げるかは、B7 の時点で上流のソースで確かめて決める（機能設計の D11）。

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q3: A、決まっていること）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（2節・D10・D11）
- コード:
  - `frontend/package.json`（46行・58行）
  - `frontend/package-lock.json`（axe-core 4.13.0、vitest-axe の依存）
  - `vendor/make-you-chic-ui/package-lock.json`（開発用の axe-core 4.13.0）
  - `frontend/e2e/support/axe.ts`
  - `frontend/.npmrc`
  - `config/npm-build-tools.txt`
  - `.github/dependabot.yml`（axe-core の `ignore`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Code Style の静的解析とセキュリティ検査、Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden）

## Assumptions & Open Questions

None.
