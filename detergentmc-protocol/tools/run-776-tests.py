#!/usr/bin/env python3
"""776 分割検証：対象テストだけを javac でコンパイルし TestRunner で実行する。

Gradle の :detergentmc-protocol:test は compileTestJava が他担当の壊れた
テストファイル（ClientProxy775Test／ClientProxy776Test／
ResourcePackServer759Test の 6 件）で止まるため、Gradle では実行できない。
そのため対象クラス（ChunkTranslator*Test／PlayTranslator*Test／
ProxySession*Test）だけを抜き出してスタンドアロンで実行する。
メインクラスは Gradle の compileJava 成果物（build/classes/java/main）を使う。
JUnit XML は split-verification-776/ に書き、重複を除いて集計する。
"""
import glob
import os
import subprocess
import sys

ROOT = "/home/hatch/workspace/detergentmc"
TOOLS = f"{ROOT}/detergentmc-protocol/tools"
MAIN_CLASSES = f"{ROOT}/detergentmc-protocol/build/classes/java/main"
API_JAR = f"{ROOT}/detergentmc-api/build/libs/detergentmc-api-0.1.0-1.20.4.jar"
SCRATCH = "/tmp/standalone776"
OUT = f"{ROOT}/split-verification-776"
JAVAC = "/home/hatch/workspace/.tools/jdk-17.0.21+9/bin/javac"
JAVA = "/home/hatch/workspace/.tools/jdk-17.0.21+9/bin/java"

GRADLE_CACHE = "/home/hatch/.gradle/caches/modules-2/files-2.1"


def find_jar(group_path, name_pattern):
    hits = glob.glob(f"{GRADLE_CACHE}/{group_path}/**/{name_pattern}", recursive=True)
    hits = [h for h in hits if "sources" not in h]
    assert hits, f"jar が見つからない: {name_pattern}"
    return hits[0]


def main():
    junit_jars = [
        find_jar("org.junit.jupiter/junit-jupiter-api", "junit-jupiter-api-*.jar"),
        find_jar("org.junit.jupiter/junit-jupiter-engine", "junit-jupiter-engine-*.jar"),
        find_jar("org.junit.jupiter/junit-jupiter-params", "junit-jupiter-params-*.jar"),
        find_jar("org.junit.jupiter/junit-jupiter", "junit-jupiter-*.jar"),
        find_jar("org.junit.platform/junit-platform-commons", "junit-platform-commons-*.jar"),
        find_jar("org.junit.platform/junit-platform-engine", "junit-platform-engine-*.jar"),
        find_jar("org.opentest4j/opentest4j", "opentest4j-*.jar"),
        find_jar("org.apiguardian/apiguardian-api", "apiguardian-api-*.jar"),
    ]
    netty_jars = glob.glob(f"{GRADLE_CACHE}/io.netty/**/**/*.jar", recursive=True)
    netty_jars = [j for j in netty_jars if "sources" not in j]
    cp = ":".join([MAIN_CLASSES, API_JAR] + junit_jars + netty_jars)

    test_src = f"{ROOT}/detergentmc-protocol/src/test/java/io/detergentmc/protocol"
    patterns = ["ChunkTranslator*Test.java", "PlayTranslator*Test.java", "ProxySession*Test.java"]
    test_files = []
    for pat in patterns:
        test_files.extend(sorted(glob.glob(f"{test_src}/{pat}")))
    # 壊れている他担当のファイルは対象パターンに含まれない（念のため除外）。
    broken = {"ClientProxy775Test.java", "ClientProxy776Test.java", "ResourcePackServer759Test.java"}
    test_files = [f for f in test_files if os.path.basename(f) not in broken]
    classes = sorted("io.detergentmc.protocol." + os.path.basename(f)[:-5] for f in test_files)
    print(f"対象テストクラス: {len(classes)} 件")

    test_classes = f"{SCRATCH}/test-classes"
    os.makedirs(test_classes, exist_ok=True)
    r = subprocess.run([JAVAC, "-encoding", "UTF-8", "-d", test_classes, "-cp", cp]
                       + test_files, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr, file=sys.stderr)
        raise SystemExit("テストのコンパイルに失敗した")
    print("テストのコンパイル OK")

    runner_classes = f"{SCRATCH}/runner-classes"
    os.makedirs(runner_classes, exist_ok=True)
    r = subprocess.run([JAVAC, "-encoding", "UTF-8", "-d", runner_classes, "-cp", cp,
                        f"{TOOLS}/TestRunner.java"], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr, file=sys.stderr)
        raise SystemExit("TestRunner のコンパイルに失敗した")

    os.makedirs(OUT, exist_ok=True)
    run_cp = ":".join([test_classes, runner_classes, MAIN_CLASSES, API_JAR] + junit_jars + netty_jars)
    r = subprocess.run([JAVA, "-cp", run_cp, "io.detergentmc.protocol.TestRunner", OUT] + classes)
    raise SystemExit(r.returncode)


if __name__ == "__main__":
    main()
