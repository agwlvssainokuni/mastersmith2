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
package cherry.mastersmith.internaldb.platform;

/**
 * 内部DB の接続先の URL から種類を判断した結果（ENT-002）。値（URL）は持たない。
 */
public sealed interface InternalDbUrlResolution {

    /**
     * 対応する種類と判断できた。
     *
     * @param kind 内部DB の種類
     */
    record Supported(InternalDbKind kind) implements InternalDbUrlResolution {}

    /** 設定されているが空（空白だけを含む）。 */
    record Empty() implements InternalDbUrlResolution {}

    /** 対応しない種類の URL。 */
    record Unsupported() implements InternalDbUrlResolution {}
}
