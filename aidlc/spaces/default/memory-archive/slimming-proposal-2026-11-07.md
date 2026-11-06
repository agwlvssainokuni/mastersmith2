# team.md・project.md の整理の案（2026-11-07、確かめ用）

今の `aidlc/spaces/default/memory/` のファイルは変えていない。案はこのディレクトリの `team.md`・`project.md` と `knowledge/aidlc-shared/` の 9 つ。依頼者の決定は A'（機能ごとの決まりを知識の置き場へ移し、`team.md` に「必ず読む」入り口を残す）。

## やり方

- **文は1字も変えない**。箇条の頭の行と、それに続く字下げの行を、塊のまま移した。移した先では、各塊の前に `<!-- 元: <ファイル> の <見出し> -->` の注を置いた。
- 元の行と、案の 2 つのファイルと移した先の行を突き合わせ、**失われた行は 0** であることを確かめた。増えたのは、`team.md` の入り口の節（11 行）と、移した先の各ファイルの冒頭の説明だけ。
- 移した先から見て「上の『共通の形』」「上の決まりどおり」は、元のファイルに残した行を指す。各ファイルの冒頭にその旨を書いた（文そのものは変えていない）。
- 入り口は `team.md` の新しい見出し `## Knowledge Pointers` に置いた。Practices Discovery の承認（`practices-promote`）が置き換えるのは Way of Working・Walking Skeleton・Testing Posture・Deployment・Code Style の 5 つの見出しだけのため、この見出しは残る。

## 大きさの前後

| ファイル | 前 | 案 |
|---|---|---|
| `team.md` | 73,997 B | 41,091 B（55%） |
| `project.md` | 66,588 B | 49,440 B（74%） |
| 毎回読み込む合計（`org.md` 14,102 B を含む） | 154,687 B | 104,633 B（68%） |
| 移した先（9 つ、担当に任せる段は始める前にすべて読む） | — | 61,980 B |

## 移したもの

| ファイル | 読む時 | 元 |
|---|---|---|
| `checklist-auth-audit.md` | 認証・認可・監査・利用者の管理に触れる Intent | team.md の認証・認可・監査のテストの一覧。project.md の Corrections の監査の接続 2 本・メールの伏せ字の読み方 |
| `checklist-roles.md` | 役割・権限に触れる Intent | team.md の役割・権限のテストの一覧。project.md の安全の決まりの差し込み口の order |
| `checklist-business-data.md` | 業務データに触れる Intent | team.md の対象DB へ書き込むテスト・業務データの E2E・業務データのテストの一覧・Code Style の業務データの行（バックエンド 7・画面 1・SQL の組み立て 1） |
| `checklist-reference.md` | 他テーブル参照に触れる Intent | team.md の参照のテストの一覧・参照ピッカーの E2E・参照の SQL |
| `checklist-timezone.md` | 日時の変換・表示に触れる Intent | team.md の日時の変換のテストの一覧・日時を扱うテストの時差の固定 |
| `checklist-nav-menu.md` | メニューの木の画面に触れる Intent | team.md の N 階層のメニューのテストの一覧 |
| `checklist-mail.md` | メールに触れる Intent | team.md の SMTP のテスト・メールのテストの一覧・メールのテンプレートのライセンスヘッダー |
| `checklist-dsl.md` | DSL の投入・読み込みに触れる Intent | team.md の DSL のテストの一覧・DSL を信頼できない入力として扱う行。project.md の Corrections の DSL の上限の経緯 3 つ |
| `ops-notes.md` | Build and Test の負荷の試験と、Deployment Pipeline 以降の段 | team.md の負荷の記録の伏せ字のテスト。project.md の Testing Posture の負荷の試験の学び 13・Deployment の運用の学び 16・Corrections の p95 の式 |

## 残したもの（毎回読み込む）

- `team.md`: 進め方・統合・CI・カバレッジ・テストの全般の決まり・権限ごとの API の認可のテストの共通の形・不安定なテストの扱い・配備・全般の Code Style・静的解析の基準。
- `project.md`: Forbidden・Mandated のすべて（固い制約）・Change Control・Decided・Tech Stack・全般の進め方と Corrections（質問の作り方・記録の書き方・レビュー役への依頼など）・配備の段の全般の学び（前の Intent の手順を正とする・CI の試行の確かめ・スモークテストの書き方など）。

## 依頼者が特に確かめるべき点

1. **inline の段は自動では読まない**: 移した先は、担当に任せる段なら始める前にすべて読む。一方、指揮役が直接進める段（inline）が自動で受け取る読み物の一覧（`inline_context_paths`）には入らない（`.claude/tools/aidlc-inline-context.ts`）。入り口の行に「必ず読む」「読んだファイルを `## Sources` に書く」と書いて補った。読んだかどうかは、成果物の `## Sources` で確かめられる。
2. **固い制約は移していない**: Forbidden・Mandated の `NEVER`・`ALWAYS` は、機能に結びつくもの（業務データの SQL・メールなど）も含めて、すべて `project.md` に残した。
3. **振り分けの境目**: 次のものは全般の決まりとして残した。
   - 内部DB（H2）のテスト・axe-core を上げるときの決まり・画面を廃止するときの E2E の扱い・自作の SVG の決まり
   - 負荷の試験の物差しを変えるときの決まり・使い捨ての環境の行の作り方
   - 移したほうがよいもの、または残したほうがよいものがあれば、行の頭を教えてほしい。
4. **置き換えるとき**: 今の 2 つのファイルの全文を `memory-archive/team-original-2026-11-07.md`・`project-original-2026-11-07.md` に置いてから置き換え、移した先を `aidlc/spaces/default/knowledge/aidlc-shared/` に置く。この案のディレクトリは、置き換えの後に消す。
