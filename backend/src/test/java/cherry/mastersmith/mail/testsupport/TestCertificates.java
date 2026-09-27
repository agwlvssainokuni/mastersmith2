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
package cherry.mastersmith.mail.testsupport;

import cherry.mastersmith.common.testsupport.TestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * テストの TLS の証明書（自己署名）を、JDK の {@code keytool} で実行のときに一時のディレクトリへ作る（基盤の設計の Q2 A）。
 *
 * <p>名前の合う証明書（{@code localhost}・{@code 127.0.0.1}）と合わない証明書（{@code other.invalid}）を作り、両方を信頼する
 * 信頼の一覧（PKCS12）を作る。証明書と鍵はリポジトリに置かない。{@code keytool} が無い・失敗したときは、原因の分かる文言で
 * テストを失敗させる（黙って飛ばさない）。
 */
public final class TestCertificates implements AutoCloseable {

    private final Path dir;

    private final String password;

    private TestCertificates(Path dir, String password) {
        this.dir = dir;
        this.password = password;
    }

    /**
     * 証明書を作る。
     *
     * @return 作った証明書（使い終えたら {@link #close()} で消す）
     */
    public static TestCertificates create() {
        try {
            Path dir = Files.createTempDirectory("mastersmith-mail-tls");
            TestCertificates certificates = new TestCertificates(dir, TestDatabase.randomSecret());
            certificates.generate("matching", "CN=localhost", "SAN=dns:localhost,ip:127.0.0.1");
            certificates.generate("mismatching", "CN=other.invalid", "SAN=dns:other.invalid");
            for (String name : List.of("matching", "mismatching")) {
                certificates.keytool(
                        "-exportcert",
                        "-rfc",
                        "-alias",
                        name,
                        "-keystore",
                        certificates.keyStore(name).toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        certificates.password,
                        "-file",
                        dir.resolve(name + ".crt").toString());
                certificates.keytool(
                        "-importcert",
                        "-noprompt",
                        "-alias",
                        name,
                        "-file",
                        dir.resolve(name + ".crt").toString(),
                        "-keystore",
                        certificates.trustStore().toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        certificates.password);
            }
            return certificates;
        } catch (IOException e) {
            throw new IllegalStateException("テストの証明書の一時のディレクトリを作れません", e);
        }
    }

    private void generate(String name, String dname, String san) {
        keytool(
                "-genkeypair",
                "-alias",
                name,
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                dname,
                "-ext",
                san,
                "-keystore",
                keyStore(name).toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                password,
                "-keypass",
                password);
    }

    private void keytool(String... args) {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        if (!Files.isExecutable(keytool)) {
            throw new IllegalStateException("JDK の keytool が見つかりません（" + keytool + "）。JDK 25 でテストを実行してください。");
        }
        List<String> command = new ArrayList<>();
        command.add(keytool.toString());
        command.addAll(List.of(args));
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool が失敗しました（" + args[0] + "）: " + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("keytool を実行できません", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("keytool の実行が中断されました", e);
        }
    }

    private Path keyStore(String name) {
        return dir.resolve(name + ".p12");
    }

    /**
     * 名前の合う・合わない証明書の両方を信頼する信頼の一覧（PKCS12）の場所を返す。
     *
     * @return 信頼の一覧のファイル
     */
    public Path trustStore() {
        return dir.resolve("trust.p12");
    }

    /**
     * 鍵の一覧と信頼の一覧のパスワード（テストの実行ごとの乱数）を返す。
     *
     * @return パスワード
     */
    public String password() {
        return password;
    }

    /**
     * 受け手の側の TLS の文脈を作る。
     *
     * @param matching 名前の合う証明書なら true、合わない証明書なら false
     * @return TLS の文脈
     */
    public SSLContext serverContext(boolean matching) {
        try (InputStream in = Files.newInputStream(keyStore(matching ? "matching" : "mismatching"))) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, password.toCharArray());
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, password.toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("テストの証明書を読めません", e);
        }
    }

    /**
     * 送る側（アプリ）に、テストの設定の中だけで信頼の一覧を渡す設定（Spring Boot の SSL の bundle）を返す。本番の設定では bundle を
     * 使わない（security-design.md の 3節）。
     *
     * @return 設定の名前と値
     */
    public java.util.Map<String, String> clientTrustSettings() {
        return java.util.Map.of(
                "spring.ssl.bundle.jks.mailtest.truststore.location",
                "file:" + trustStore(),
                "spring.ssl.bundle.jks.mailtest.truststore.password",
                password,
                "spring.ssl.bundle.jks.mailtest.truststore.type",
                "PKCS12",
                "spring.mail.ssl.bundle",
                "mailtest");
    }

    @Override
    public void close() {
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            throw new IllegalStateException("テストの証明書を消せません", e);
        }
    }
}
