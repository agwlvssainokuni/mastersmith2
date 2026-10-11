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
package cherry.mastersmith.role.transfer;

import java.util.Objects;

/**
 * 確かめ・適用で受けた権限の YAML の本文のバイト列（NFR1.9）。読み込みの部品にはバイト列のまま渡し、文字列にしない。
 *
 * <p>{@link #toString()} は大きさだけを出し、中身（ロールの名前・対象の名前・値）を出さない。
 */
public final class RoleTransferPayload {

    private final byte[] bytes;

    /**
     * 作る。
     *
     * @param bytes 本文のバイト列（写して持つ）
     */
    public RoleTransferPayload(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    /**
     * 本文のバイト列を返す（写しを返す）。
     *
     * @return 本文のバイト列
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * 本文のバイト数を返す。
     *
     * @return バイト数
     */
    public int size() {
        return bytes.length;
    }

    /**
     * 本文の SHA-256 の 16 進数の小文字を返す（監査の detail の {@code fileSha256}。BR11.7）。
     *
     * @return SHA-256 の 16 進数の小文字 64 文字
     */
    public String sha256() {
        return TransferFingerprint.sha256Hex(bytes);
    }

    /** 大きさだけを出す。 */
    @Override
    public String toString() {
        return "RoleTransferPayload[bytes=" + bytes.length + "]";
    }
}
