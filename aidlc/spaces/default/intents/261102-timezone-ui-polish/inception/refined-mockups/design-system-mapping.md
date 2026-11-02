# デザインシステムとの対応（Intent 261102-timezone-ui-polish）

make-you-chic-ui（`vendor/make-you-chic-ui`、今の固定先 `cae5fed`）の部品への対応。部品の口は、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/index.ts` の出力で確かめた（報告ではなくソース）。`vendor/make-you-chic-ui` はこのリポジトリから直接変えない（`project.md` Forbidden）。

## 1. 部品の対応

| 画面・要素 | 部品 | 対応 |
|---|---|---|
| 表示のタイムゾーンの選択（S1） | `Select`・`FormField` | そのまま使える。地域ごとのまとまり（optgroup 相当）の可否は機能設計で `Select` のソースで確かめる |
| 保存済みの名前を解釈できない注意（S1） | `Alert`（warning） | そのまま使える |
| 保存の知らせ（S1） | `ToastProvider`・`useToast` か今の画面の知らせ | 今のプリファレンスと同じ |
| 案内の1行とリンク（S2） | 素の段落と react-router の `Link` | 部品は不要。見た目は今の `page-description` に合わせる |
| 一覧・詳細の表（S2） | `Table` | 今のまま。時刻の値は文字列として渡す |
| 時点を表す型の入力・検索（S3・S4） | `TextInput`・`FormField` | そのまま使える |
| 重なる時刻の2択（S3） | `RadioGroup`・`Radio` | そのまま使える（legend を渡す） |
| ホームのカード（S5） | `Card` | カードを1つのリンクにする使い方は、`Card` のソースで確かめる（できなければ、リンクの中に `Card` の見た目を置く） |
| ホームの見出し・並び（S5） | 素の `h2`・`ul` | 部品は不要 |
| サイドバーのアイコン（S6） | `AppShell` のサイドバーの項目の `icon` | 今の口で足りる。許す名前は `IconName` の18個 |
| ログインのアイコン（S7） | `Icon` か素の SVG | アプリのアイコンは自作の SVG。ラベルは付けない（飾り） |
| 上のバーのロゴ（S8） | `Topbar`（`AppShell`） | **口が無い（上流に依頼）** |

## 2. 足りない点（上流への依頼）

文面は `make-you-chic-ui-request-8.md` に置く（`team.md` Way of Working）。

| # | 足りない点 | 使う場所 | ストーリー | 取り込むまでの扱い |
|---|---|---|---|---|
| 1 | アイコン `mail`・`users`・`shield` | 管理の区画の招待・グループ・ロール | AC4.3.3（Should） | アイコンを付けない（今のまま） |
| 2 | アイコン `table`・`view`・`folder` | 業務メニューの既定とホームのカード | AC4.4.2 | 今の既定のまま（1種類） |
| 3 | `Topbar` にロゴを置く口 | 上のバーのロゴ | US4.2（Should） | ロゴは出さない |

取り込みは、その部品を使う Bolt の前に、固定先の更新の専用のコミットで行う。固定先を上げる前に、新しいコミットが上流の `main` から辿れることと、`package-lock.json` の前後の差（`hasInstallScript` の付いた項目が増えていないか）を、読み取りで確かめて記録する（`team.md`）。待ちが長引くときの自前の SVG への切り替えは、その時点で依頼者に諮る。

## 3. アイコンの名前の一覧のそろえ（FR11.4）

許すアイコンの名前の一覧は、画面の側（`frontend/src/app/registry/allowedIcons.ts`）とサーバーの側（`backend/src/main/resources/navigation/allowed-icons.txt`）の2か所にある。固定先のアイコンを増やしたら、2か所を同じ名前にそろえる（型の検査が画面の側の欠けを教える。サーバーの側は、そろいを確かめるテストをコード生成で足す。追跡で Code Generation へ Deferred）。

## 4. 自作する部品（`src/shared/` か機能の中）

| 部品 | 置き場 | 理由 |
|---|---|---|
| 時刻の整形・解釈（表示のタイムゾーンでの変換） | `frontend/src/shared/format/` | 変換を1か所に閉じる（FR4.2）。部品ではなく関数 |
| 表示のタイムゾーンの案内の1行 | 業務データの機能の中 | 業務データの画面だけが使う |
| ホームのカードの並び | `frontend/src/app/pages/` | 今の `HomePage` に足す |
| アプリのアイコンの SVG・favicon | `frontend/public/`（SVG・`.ico`・`.png`）とログインの画面 | 自作。ヘッダーを置き、検査の対象に `.svg` を足す |

## 5. 見た目の決まり

- 色・余白は、make-you-chic-ui のトークンと今の `page.css` のクラスを使う。ブランドカラーとテーマ（ライト・ダーク・OS に合わせる）のすべての組で、コントラストを確かめる。
- アイコンの大きさは、サイドバーは今の大きさ、ホームのカードは 24 px、ログインは 48 px 程度。
