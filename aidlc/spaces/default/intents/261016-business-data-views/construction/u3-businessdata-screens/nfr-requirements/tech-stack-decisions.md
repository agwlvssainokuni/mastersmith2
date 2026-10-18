# 技術の選択 — U3 u3-businessdata-screens

U3 は新しい依存を足さない。決めるのは、今の道具のどれを使うかと、入れないもの（表の仮想化）だけ。

## 上流の ID との対応

| 上流 | この文書で扱うこと | 要件の ID |
|---|---|---|
| NFR1 応答時間・NFR2 規模 | 表の仮想化を入れないこと | NFR1.7・NFR2.10（`performance-requirements.md`） |
| NFR4 セキュリティ | 依存を足さないこと、印の乱数 | NFR4.22（`security-requirements.md`） |
| NFR5 アクセシビリティ | axe-core の版の扱い | NFR5.6・NFR5.8（`security-requirements.md`） |
| NFR7 テスト | テストの道具 | NFR7.9〜NFR7.13（`security-requirements.md`） |

## 選択

| 道具 | 決め | 理由 |
|---|---|---|
| React・TypeScript・react-router・i18next | 今の版のまま | 画面と道と文言は今の骨組みで作れる |
| make-you-chic-ui | U4 が B3 で上げる `b0a1e79` を使う（Table・Dropdown・Modal・FormField・TextInput・Textarea・Select・RadioGroup・Checkbox・Switch・Tooltip・Toast・Alert） | 部品の口はソースで確かめ済み（機能設計の Sources）。依頼（5）が取り込まれたら、B7 で横のスクロールの枠と件数の選択を make-you-chic-ui の口に置き換える（U4 の D11） |
| 表の仮想化 | 入れない。100 件 × 100 カラムをそのまま描く | 新しい依存か自前の作りが要る。NFR1.7 の測りで Not Met になったときに、承認の場で諮る（Q1: A） |
| 主キーの印 | ブラウザの `crypto.getRandomValues` で作る | 標準の API で、新しい依存ではない |
| Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright | 今の版のまま | 画面のテスト・性質ベースのテスト・E2E は今の道具で書ける |
| axe-core | 4.13 のまま統合する。4.14 は、B5・B6 の終わりに作業フォルダの中だけで試す（D31） | 引き上げは U4 の B7 が受け持つ。試しの版は `package.json`・`package-lock.json` の差で戻したことを確かめる |

- 新しい依存を足すことになったときは、採用の前にライセンスを確かめ、Apache License 2.0 と異なるなら理由を記録に残す（team.md）。
- npm は `frontend/.npmrc` の `ignore-scripts=true` のままにする。

## 上流との差

1. **承認の場の決定**: `nullable` は U2 が足し、`formPart` の値域から `lookup` を外し、U3 は欠けた項目を形の崩れとする。機能設計の 6.2 の `text`・`lookup` の行は `text` だけと読み替える。技術の選択には響かない。
2. **413 の行が増える**: 6.1 に 413 の文を足す（`security-requirements.md` の NFR4.23）。技術の選択には響かない。
3. **team.md の E2E の読み方**: 業務データを表示する E2E の前提（PostgreSQL）は代表の流れ 180 に当て、170・185 は型の付いた見本で応答を差し替える。新しい道具は要らない（Playwright の `page.route` を使う）。
4. **ID の振り方**: NFR7 は U4 との重なりを避けて 7.8 から振る（U2 の割り振りでは 7.7 から）。U4 の NFR4.5〜4.9・NFR7.1〜7.7 が U1・U2 と重なることは、承認の場で諮る。

## 後の段に回すこと

- **NFR 設計**: 記憶の作り、`markClean()` の旗の作り（どちらも新しい依存を使わない）。
- **Infrastructure Design**: E2E のアプリから見本の PostgreSQL に届く形（`compose.yaml` の profile やポートの開け方）。
- **コード生成の計画**: R-04・R-06、主キーのカラムを更新で変えるときの扱い。B5・B6 の前後の初回の JavaScript の大きさの記録。

## U1・U2・U4 とそろえる点

- U1・U2 の tech-stack にも新しい依存は無い。Intent 全体で新しい依存を足さない。
- make-you-chic-ui の固定先と axe-core の版は U4 が上げ、U3 はそれに乗る。
- テストの道具は U4 と同じもの（fast-check・vitest-axe・Playwright）を使う。

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D27・D31、Sources）、`frontend-components.md`（2節）
- U4: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/tech-stack-decisions.md`
- U1: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/tech-stack-decisions.md`
- コード: `frontend/package.json`、`frontend/.npmrc`、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/`（Table・Tooltip ほか）
- 決まり: `aidlc/spaces/default/memory/team.md`（Code Style の静的解析とセキュリティ検査）

## Assumptions & Open Questions

None.
