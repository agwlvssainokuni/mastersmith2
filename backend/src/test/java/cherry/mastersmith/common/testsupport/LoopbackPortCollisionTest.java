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
package cherry.mastersmith.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * テストのアプリの待ち受けの番号が、ほかの IPv4 の全アドレスの待ち受けと重ならないことの確かめ
 * （Intent 260925-storage-memory-fixes の FR3.2、{@code AccessTokenApiIT} の一時的な失敗の直し）。
 *
 * <p>colima は、Testcontainers が公開したコンテナの番号を、VM の外（この PC）の IPv4 の全アドレス（{@code *:<番号>}）で待ち受けて
 * 転送する。Java の既定では、テストのアプリ（Tomcat）が全アドレスで待ち受けるとき IPv6 の全アドレス（{@code [::]}、IPv4 と兼用）を
 * 使い、macOS では同じ番号を重ねて取れてしまう。重なると、{@code localhost} への要求が colima の転送からコンテナへ届いて切られ、
 * 「HTTP/1.1 header parser received no bytes」で失敗する（計画の前の確かめで同じ文言を再現した）。
 *
 * <p>そのため、テストの JVM に {@code -Djava.net.preferIPv4Stack=true} を渡し（{@code backend/build.gradle.kts} のすべての
 * テストのタスク）、テストのアプリも IPv4 の全アドレスで待ち受けるようにする。ここでは、テストの中で開いた待ち受けだけで、
 * 同じ番号を取れないこと（{@link BindException}）を確かめる。コンテナや colima には頼らない。
 */
class LoopbackPortCollisionTest {

    @Test
    @DisplayName("the test JVM prefers the IPv4 stack")
    void testJvmPrefersIpv4Stack() {
        assertThat(System.getProperty("java.net.preferIPv4Stack"))
                .as("backend/build.gradle.kts のテストのタスクで -Djava.net.preferIPv4Stack=true を渡す")
                .isEqualTo("true");
    }

    @Test
    @DisplayName("a wildcard listener in the test JVM cannot take a port already held on the IPv4 wildcard address")
    void wildcardListenerCannotTakePortHeldOnIpv4Wildcard() throws IOException {
        InetAddress ipv4Any = Inet4Address.getByName("0.0.0.0");
        // colima の転送の役: IPv4 だけのソケット（AF_INET）で、IPv4 の全アドレスの空いた番号を1つ取る。Java の既定のソケットは
        // IPv6 と兼用（AF_INET6）で、IPv4 のアドレスで待ち受けても colima の転送とは違う形になり、重なりを再現できないため。
        try (ServerSocketChannel forwarder = listener(StandardProtocolFamily.INET)) {
            forwarder.bind(new InetSocketAddress(ipv4Any, 0));
            int port = ((InetSocketAddress) forwarder.getLocalAddress()).getPort();

            // テストのアプリ（Tomcat）の役: アドレスを指定せず（全アドレス）、同じ番号で待ち受けようとする。
            try (ServerSocketChannel app = listener()) {
                assertThatThrownBy(() -> app.bind(new InetSocketAddress(port)))
                        .as("番号 %d が重ねて取れてはいけない", port)
                        .isInstanceOf(BindException.class);
            }
        }
    }

    @Test
    @DisplayName("a wildcard listener in the test JVM can take a free port")
    void wildcardListenerCanTakeFreePort() throws IOException {
        try (ServerSocketChannel app = listener()) {
            app.bind(new InetSocketAddress(0));

            InetSocketAddress local = (InetSocketAddress) app.getLocalAddress();
            assertThat(local.getPort()).isPositive();
            assertThat(local.getAddress()).isInstanceOf(Inet4Address.class);
        }
    }

    /** Tomcat と同じく、JVM の既定の種類で SO_REUSEADDR を有効にした待ち受けを作る。 */
    private static ServerSocketChannel listener() throws IOException {
        return reuseAddress(ServerSocketChannel.open());
    }

    /** 種類（IPv4 だけ など）を指定して、SO_REUSEADDR を有効にした待ち受けを作る（重なりやすい側の条件）。 */
    private static ServerSocketChannel listener(StandardProtocolFamily family) throws IOException {
        return reuseAddress(ServerSocketChannel.open(family));
    }

    private static ServerSocketChannel reuseAddress(ServerSocketChannel channel) throws IOException {
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        return channel;
    }
}
