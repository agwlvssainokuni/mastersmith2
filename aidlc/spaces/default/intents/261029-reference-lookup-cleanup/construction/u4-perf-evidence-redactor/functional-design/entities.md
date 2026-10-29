# エンティティ — U4 u4-perf-evidence-redactor

出典: 単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U4）、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR5・NFR8）、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（PerfEvidenceRedactor）。

この単位は、アプリが保存するデータ（内部DB の表・対象DB の行）を持たない。`project.md` の決まり（機能設計のエンティティにはアプリが独自に持つデータだけを書く）に従い、保存するエンティティは置かない。

ただし、伏せ字の道具（PerfEvidenceRedactor）の振る舞いを決まり（`rules.md`）と流れ（`functional-spec.md`）で読み違えないよう、道具の中だけで扱う一時の形（メモリの上の形）を、参考として下に並べる。どれも保存せず、道具が終われば消える。

```yaml
persisted_entities: []   # 保存するエンティティは無い（アプリの実行時にも含まれない）

transient_shapes:        # 道具の中だけの一時の形（参考。保存しない）
  - name: InputDocument
    description: 入力の k6 の summary json（--summary-export の出力）を JSON として読んだ木
    attributes:
      - name: sourcePath
        type: text
        required: true
        constraints: "存在する読めるファイル。出力のパスと同じであってはならない（BR3.2）"
      - name: tree
        type: json-value
        required: true
        constraints: "空でない JSON。任意の JSON の値（オブジェクト・配列・文字列・数・真偽・null）を受ける（BR3.1）"
    notes: "k6 の summary json の上の階層は root_group・metrics・setup_data。JWT は setup() が返す値（setup_data）に入る（functional-spec.md 7節）"

  - name: InputText
    description: テキストの置換の口（--text）の入力。k6 の標準出力・ログなど JSON でないテキストを UTF-8 として読んだ 1 つの文字列
    attributes:
      - name: sourcePath
        type: text
        required: true
        constraints: "存在する読めるファイル。出力のパスと同じであってはならず、出力の拡張子は .json であってはならない（BR3.2・BR3.4）"
      - name: content
        type: text
        required: true
        constraints: "空（空白だけ）でない、UTF-8 として正しい、NUL を含まない。上の階層に setup_data を持つ JSON（summary json）は受けない（BR3.4）"
    notes: "中身全体に BR1.2 と同じ置換の関数を当てる。改行・空白・BOM は変えない（BR1.8）。置換の後は RedactionResult の replacedCount と serializedText（出力の文字列）だけを使う"

  - name: RedactionResult
    description: 置換を当てた後の木と、書き出す前の文字列
    attributes:
      - name: tree
        type: json-value
        required: true
        constraints: "入力と同じ構造（キーの集合と値の型）。JWT の形の部分が [REDACTED-JWT] に、上の階層の setup_data の下の文字列の値がすべて [REDACTED-SETUP] に替わる（BR1.2〜BR1.7）"
      - name: replacedCount
        type: integer
        required: true
        min: 0
        constraints: "置換した JWT の数。値そのものは持たない"
      - name: setupRedactedCount
        type: integer
        required: true
        min: 0
        constraints: "setup_data の下で [REDACTED-SETUP] にした文字列の数。値そのものは持たない（BR1.7）"
      - name: serializedText
        type: text
        required: true
        constraints: "書き出す直前の JSON の文字列。残存の検査はこの文字列に当てる（BR2.1）"

  - name: ResidualFinding
    description: 残存の検査（置換とは独立の関数）が見つけた JWT の形の1件。値そのものは持たない
    attributes:
      - name: location
        type: text
        required: true
        constraints: "ファイルのパス（evidence の検査のとき）と、文字列の中の位置（行と桁、またはバイトの位置）。見つけた値・前後の文字は持たない（BR2.3）"
    relationships: []

  - name: KeyCollision
    description: キー名の置換で、同じオブジェクトの中の2つのキーが同じになったこと
    attributes:
      - name: objectPath
        type: text
        required: true
        constraints: "衝突したオブジェクトの位置を、置換の後のキー名でたどった道（[REDACTED-JWT] を含みうるが、元の値は含まない）"
```

## 要約

- 保存するエンティティは無い。道具はアプリの実行時に含まれず、`perf/` の手順の道具としてリポジトリに置く（unit-of-work の U4「配備の形」）。
- 一時の形は5つ。入力の木（InputDocument）、テキストの置換の口の入力（InputText）、置換の後の木と書き出す前の文字列（RedactionResult。テキストの置換の口では木を持たず文字列だけ）、残存の検査の1件（ResidualFinding）、キーの衝突（KeyCollision）。後の2つは、見つけた値そのものを持たないことが要点で、`project.md` の Forbidden（認証の値を伏せずに perf の記録へ置かない）を道具の誤りの表示でも破らないための形である。
