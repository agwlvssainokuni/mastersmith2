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
package cherry.mastersmith.dslmanage.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * 保存した DSL の本文と、その参照の組（プレビュー・適用の履歴の1件）。本文は受け取ったバイト列のまま（ADR-003）。
 *
 * <p>文字列化では本文を出さない（ログに本文を出さない。NFR1.17）。本文は写して持ち、写して返す。
 *
 * @param <R> 参照の型（{@link DslPreviewRef} または {@link DslAppliedRef}）
 * @param ref 参照
 * @param yamlBytes 本文のバイト列
 */
public record DslContent<R>(R ref, byte[] yamlBytes) {

    /** 必須の値を確かめ、本文を写して持つ。 */
    public DslContent {
        Objects.requireNonNull(ref, "ref は必須です");
        yamlBytes = Objects.requireNonNull(yamlBytes, "yamlBytes は必須です").clone();
    }

    /**
     * 本文の写しを返す。
     *
     * @return 本文のバイト列
     */
    @Override
    public byte[] yamlBytes() {
        return yamlBytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DslContent<?> that && ref.equals(that.ref) && Arrays.equals(yamlBytes, that.yamlBytes);
    }

    @Override
    public int hashCode() {
        return 31 * ref.hashCode() + Arrays.hashCode(yamlBytes);
    }

    /** 本文を出さない文字列。 */
    @Override
    public String toString() {
        return "DslContent[ref=" + ref + ", bytes=" + yamlBytes.length + "]";
    }
}
