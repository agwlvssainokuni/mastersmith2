# Build and Test — Questions

## Q1. colima の VM の作り直し（要件 FR1.2）

負荷の試験（FR5）と F3 の内訳の測定（FR4）の前に、colima の VM を CPU 4・メモリ 6GiB に作り直す（`colima stop` → `colima start --cpu 4 --memory 6`）。VM を止めると、動いている配備したアプリ（`mastersmith-app-1`）も止まる。内部DBのボリューム（`mastersmith_mastersmith-data`）は VM のディスクに残る。

A. 作り直す。その前に内部DBのボリュームのバックアップ（`mastersmith-data-<日時>.tgz`、Git 管理外）を取る
B. 作り直す（バックアップは取らない）
C. 今は作り直さない（負荷の試験と測定を保留する）
X. Other (please specify)

[Answer]: A
