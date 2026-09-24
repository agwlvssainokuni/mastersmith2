/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.dslmanage.generate;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.serializer.Serializer;

/**
 * DSL の値の木を YAML の本文に書き出す（BR5.1、NFR4.7・NFR4.8、NFR 設計 security-design.md の 2節・3節）。
 *
 * <p>SnakeYAML の書き出しの部品（{@code Representer}・{@code Serializer}・{@code Emitter}。{@code Yaml.dump} と同じ
 * 組み合わせで、型を作る仕組みを持たない）に、固定の書き出しの形（ブロックの形、字下げ 2、1行の幅は無制限、改行は LF、別名を
 * 作らない、Unicode の文字をそのまま書く、表せない文字はエスケープ）を渡す。名前・コメントは文字列として部品に渡し、引用が要る
 * かは部品が決める。ただし YAML が読むときに LF にそろえてしまう改行の文字（CR・U+0085・U+2028・U+2029）と LF を含む文字列は、
 * 二重引用符の形でエスケープして書き、読み直した値が元と同じになるようにする。文字列の連結で YAML を組み立てない（先頭の固定の
 * コメント1行だけを前に付ける）。日時・乱数・実行の環境の値を入れないため、同じ木からはいつも同じバイト列になる。
 *
 * <p>木は {@code Map}・{@code List}・{@code String}・{@code Integer}・{@code Long}・{@code Boolean}・null だけで組み立てる
 * （ほかの型はタグ付きで書き出され、U2 がタグを拒否するため）。
 */
@Component
public class DslYamlWriter {

    /** 本文の先頭に付ける、生成したことを示すコメントの行（固定の文字列）。 */
    public static final String HEADER_COMMENT = "# generated from the target database schema by MasterSmith";

    /** 字下げの幅。 */
    private static final int INDENT = 2;

    /** 単純なキーとして書く長さの上限（SnakeYAML が受け付ける最大）。これより長いキーは部品が複雑なキーの形で書く。 */
    private static final int MAX_SIMPLE_KEY_LENGTH = 1024;

    private static final char DELETE = '\u007F';

    private static final char C1_FIRST = '\u0080';

    private static final char C1_LAST = '\u009F';

    /**
     * 値の木を YAML の本文（UTF-8）にする。
     *
     * @param tree DSL の値の木（{@link DslTreeBuilder} が作ったもの）
     * @return 先頭のコメント1行と YAML の本文のバイト列
     */
    public byte[] write(Map<String, Object> tree) {
        DumperOptions options = dumperOptions();
        StringWriter out = new StringWriter();
        out.write(HEADER_COMMENT);
        out.write('\n');
        Serializer serializer = new Serializer(new Emitter(out, options), new Resolver(), options, null);
        try {
            serializer.open();
            serializer.serialize(new DslRepresenter(options).represent(tree));
            serializer.close();
        } catch (IOException e) {
            // 書き出す先は StringWriter で、入出力の失敗は起きない。起きたら想定外の失敗として上に伝える。
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 固定の書き出しの形を返す（呼ぶたびに新しく作る。書き出しの部品はスレッドの間で共有しない）。
     *
     * @return 書き出しの形
     */
    static DumperOptions dumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setIndent(INDENT);
        options.setIndicatorIndent(INDENT);
        options.setIndentWithIndicator(true);
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setDereferenceAliases(true);
        options.setAllowUnicode(true);
        options.setNonPrintableStyle(DumperOptions.NonPrintableStyle.ESCAPE);
        options.setMaxSimpleKeyLength(MAX_SIMPLE_KEY_LENGTH);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);
        options.setProcessComments(false);
        return options;
    }

    /**
     * 対象DB のコメントから制御文字を取り除く（NFR4.8）。改行（LF）とタブを除く C0（U+0000〜U+001F）の制御文字、U+007F、C1
     * （U+0080〜U+009F）の制御文字を取り除き、長さは切り詰めない。取り除いた後に空・空白だけになったら、コメントが無いもの（null）
     * として扱う。
     *
     * @param comment 対象DB のコメント（無ければ null）
     * @return 制御文字を取り除いたコメント（無ければ null）
     */
    public static String stripControlCharacters(String comment) {
        if (comment == null) {
            return null;
        }
        StringBuilder kept = new StringBuilder(comment.length());
        for (int i = 0; i < comment.length(); i++) {
            char c = comment.charAt(i);
            if (!isRemovedControl(c)) {
                kept.append(c);
            }
        }
        String result = kept.toString();
        return result.isBlank() ? null : result;
    }

    /**
     * YAML の改行の文字（LF・CR・U+0085・U+2028・U+2029）を含むか。
     *
     * @param value 文字列
     * @return 含むなら true
     */
    static boolean containsLineBreak(String value) {
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\n', '\r', '\u0085', '\u2028', '\u2029' -> {
                    return true;
                }
                default -> {
                    // 改行ではない。
                }
            }
        }
        return false;
    }

    private static boolean isRemovedControl(char c) {
        if (c == '\n' || c == '\t') {
            return false;
        }
        return c < ' ' || c == DELETE || (c >= C1_FIRST && c <= C1_LAST);
    }

    /** 改行を含む文字列だけを二重引用符の形にする表し方（ほかは SnakeYAML の既定の表し方のまま）。 */
    private static final class DslRepresenter extends Representer {

        DslRepresenter(DumperOptions options) {
            super(options);
            setDefaultFlowStyle(options.getDefaultFlowStyle());
            setDefaultScalarStyle(options.getDefaultScalarStyle());
        }

        @Override
        protected Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
            if (Tag.STR.equals(tag) && containsLineBreak(value)) {
                return super.representScalar(tag, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            }
            return super.representScalar(tag, value, style);
        }
    }
}
