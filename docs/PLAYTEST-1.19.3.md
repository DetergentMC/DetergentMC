# DetergentMC 1.19.3 実機プレイテスト — チェックリスト

2026-11-13作成。1.19.3クライアント（プロトコル761）で1.19.3・1.19.4・
1.20系・1.21系サーバーに参加する実機確認のための手順と、結果の記録用。既存の
`PLAYTEST-1.20.1.md`（1.20.1クライアント用）・`PLAYTEST-1.20.2.md`
（1.20.2クライアント用）・`PLAYTEST-1.20.4.md`
（1.20.4クライアント用）・`PLAYTEST-1.20.5.md`
（1.20.5／1.20.6クライアント用）・`PLAYTEST-1.21.md`
（1.21／1.21.1クライアント用）・`PLAYTEST-1.21.2.md`
（1.21.2／1.21.3クライアント用）・`PLAYTEST-1.21.4.md`
（1.21.4クライアント用）・`PLAYTEST-1.21.5.md`
（1.21.5クライアント用）・`PLAYTEST-1.21.6.md`
（1.21.6クライアント用）・`PLAYTEST-1.21.7.md`
（1.21.7／1.21.8クライアント用）・`PLAYTEST-1.21.9.md`
（1.21.9／1.21.10クライアント用）・`PLAYTEST-1.21.11.md`
（1.21.11クライアント用）・`PLAYTEST-1.19.4.md`
（1.19.4クライアント用）は直接いじらず、
こちらを1.19.3専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.19.3対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ子プロセス＋生ソケットで761クライアントを
装った統合テスト `ProxyIntegrationTest`、`ChunkTranslator761Test`、
`PlayTranslator761Test` など）。実機テストの目的は、プロトコルレベルでは
見えない「本物のゲームクライアントでの見た目・挙動」を確かめることである。
以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- 761クライアントの1.19.3サーバー（内部761）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake761ClientJoinsRealVanilla1193ThroughProxy`）。
  素通し（リレー）。Join Game の codec に dimension_type と biome が含まれることを確認
- 761クライアントの1.19.4サーバー（内部762）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake761ClientJoinsRealVanilla1194ThroughProxy`）。
  ログイン成功のあと両方ともすぐプレイ状態へ進む。Join Game は0x24のまま。
  位置の dismountVehicle を落として送る。Window Click／Set Creative Slot／
  Update Sign を変換する。762にだけある Damage Event／Bundle Delimiter／
  Chunk Biomes／Hurt Animation を落とす
- 761クライアントの1.20.1サーバー（内部763）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake761ClientJoinsRealVanilla1201ThroughProxy`）。
  Join Game の portalCooldown を落として送る。761→762→763の変換
- 762クライアントの1.19.3サーバー（内部761）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake762ClientJoinsRealVanilla1193ThroughProxy`）。
  761サーバーの Join Game に portalCooldown=0 を補うのは763向けだけで、
  762向けはそのまま通す
- 763クライアントの1.19.3サーバー（内部761）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake763ClientJoinsRealVanilla1193ThroughProxy`）。
  761サーバーの Join Game に portalCooldown=0 を補って763形式にしてから送る
- 本物の1.19.3の Join Game codec の中身（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1193`）：
  chat_type・dimension_type・worldgen/biome の3レジストリだけ。
  **minecraft:damage_type レジストリは無い**（1.19.4で追加）。
  バイオーム63件・plains=38・cherry_grove 無し
- 761↔762のレジストリ差分（ViaVersion `mapping-1.19.3.json` と minecraft-data で確認）：
  パケットID 132件変更（新規2件を含むと134件）、ブロック状態 23,232→23,725件
  （+493。cherry 系など）、アイテム 1,186→1,228件（+42）、エンティティ
  119→124件（display 系・sniffer・text_display）、音 1,392→1,454件（+62）、
  パーティクル 93→96件（cherry_leaves 系3種）、エンチャント 39件・同じ、
  エフェクト 33件・同じ（ただし765は0ベース、761・762は1ベース）、
  バイオーム 63件・同じ、ダメージ種別は761に存在しない、メニュー 24→25件
  （legacy_smithing の分離）、ブロックエンティティ種別 38→40件
  （suspicious_sand・decorated_pot）、絵画 30件・同じ、楽器 23件・同じ、属性 13件・同じ
- 761用リソースパック（pack_format 12）の生成と配信
  （`ResourcePackBuilder761Test`、`ResourcePackServer761Test`）
- `/trigger d_hello` / `d_lang_ja` / `d_lang_en` の動作（言語切替と保存を含む。
  761クライアントでも同じ。直接 `/hello` と打っても動かない。`/trigger d_hello` を使うこと）

### B. 実機でしか確認できない項目（この手順書の中心）

- 本物の1.19.3クライアントが、1.19.4サーバーの Join Game codec
  （damage_type レジストリ付き）を無視して参加できるか
- 本物の1.19.3クライアントが、1.20.1／1.21.11サーバーの Join Game codec
  （trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧）を無視して参加できるか
- 1.19.4サーバー側の新しいブロック（cherry 系、suspicious_sand、decorated_pot、
  torchflower、pink_petals、sniffer 関連）が1.19.3クライアントに
  どう見えるか（代替：oak 系、sand、flower_pot、dandelion、peony）
- 1.19.4サーバー側の新しいアイテム（cherry 系、sniffer_spawn_egg、torchflower、
  pink_petals、torchflower_seeds、brush、鍛冶型テンプレート11件、陶片4件、
  cherry_boat／chest_boat、cherry_sign／hanging_sign、decorated_pot、
  suspicious_sand）が1.19.3クライアントにどう見えるか
  （代替：oak 系、turtle_spawn_egg、dandelion、peony、wheat_seeds、stick、
  netherite_ingot／iron_ingot、brick、oak_boat／oak_chest_boat、
  oak_sign／oak_hanging_sign、flower_pot、sand）
- 1.19.4サーバー側の新しいエンティティ（display 系・sniffer・text_display）が
  1.19.3クライアントにどう見えるか（代替：ブタ）
- 1.19.4サーバー側の新しい音（cherry_wood 系、sniffer 系、decorated_pot、
  suspicious_sand、music.overworld.cherry_grove）が1.19.3クライアントで
  どう聞こえるか（代替：木・草・石・砂の音、ブタの音、jungle_and_forest）
- 1.19.4サーバー側の新しいパーティクル（cherry_leaves 系3種）が
  1.19.3クライアントでどう見えるか（代替：white_ash）
- 1.19.4サーバーの suspicious_sand・decorated_pot の中身（ブロックエンティティ）が
  1.19.3クライアントでどう見えるか（ブロックエンティティは落とすため、中身は表示されないはず）
- 1.19.4サーバーの鍛冶台（smithing。3スロット）が1.19.3クライアントで
  どう見えるか（旧2スロットの smithing に写す。鍛冶型を入れたときの挙動）
- 1.19.3クライアントに761用パック（pack_format 12）が提示され、適用されるか
- **2人同時参加（他プレイヤーの出現・スキン・チャット）**。`named_entity_spawn`（0x02）を
  実バニラ1.19.3が送るかどうかは未確認。偽クライアントでは、直接接続でも
  プロキシ経由でも、他プレイヤーの出現パケットが30秒以内に届かなかった。
  **実機クライアントで2人同時に遊び、相手が見えるかどうかを確かめるのが
  最も重要な残りの検証である**。相手が見えない場合は、ログの文言と一緒に報告すること。
  また、761クライアントが Spawn Entity（player種別）を受け入れるかどうかも
  ここで確かめる（プロキシは761＋761では素通しのため、1.19.3サーバーが
  Spawn Entity を送ってきた場合、そのままクライアントに届く）
- 764以上のクライアント（1.20.2以降）が761サーバーに参加したとき、
  damage_type レジストリ無しの合成設定フェーズを受け入れるか

## 1. 起動

### 1.1 準備（初回のみ）

```sh
cd ~/workspace/detergentmc
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

`run/eula.txt` と `run/vanilla/eula.txt` が `eula=true` になっていること。

### 1.2 起動（ファットjar・推奨）

```sh
cd ~/workspace/detergentmc
JAVA_HOME=/home/hatch/workspace/.tools/jdk-17.0.21+9 \
  java -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

`detergentmc.yml` の `minecraft-version` を `1.19.3` にして起動すると、
内部761サーバーが Java 17 で起動する。`1.19.4` にすると内部762サーバー、
`1.20.1` にすると内部763サーバーになる（切り替えて試すこと）。

### 1.3 接続（実機クライアント）

Minecraft Java Edition 1.19.3 のクライアントで `127.0.0.1:25565` に接続する。
オフラインモード（`online-mode: false`）の場合は、ランチャーのプロファイルで
オフラインモードを有効にするか、適当な名前で接続すること。

## 2. チェックリスト（1.19.3クライアント → 内部761サーバー）

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 地面・木・水などの見た目が正しい（ブロック状態の変換が正しい）
- [ ] 移動できる（歩く・走る・泳ぐ・ジャンプ）
- [ ] チャットができる（`/trigger d_hello` で挨拶が出る。直接 `/hello` は動かない）
- [ ] `/trigger d_lang_en` で英語に切り替わる。`/trigger d_lang_ja` で日本語に戻る
- [ ] リソースパックが提示される（pack_format 12）。適用される
- [ ] インベントリを開ける。アイテムを動かせる（Window Click の変換）
- [ ] クリエイティブでアイテムを取れる（Set Creative Slot の変換）
- [ ] 看板を立てて文字を書ける（Update Sign の変換）
- [ ] 死んでリスポーンできる（Respawn の変換）
- [ ] 2人目が参加したとき、相手が見える（Player Info・出現パケット）。
  **ここが最重要の未確認項目**。相手が見えない・相手がブタに見える・
  姿がおかしい場合は、そのままの様子を報告すること

## 3. チェックリスト（1.19.3クライアント → 内部762サーバー）

`minecraft-version` を `1.19.4` にして起動し、1.19.3クライアントで接続する。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 桜の木・桜の葉がどう見えるか（代替：樫の木・樫の葉）
- [ ] 疑わしい砂・飾り壺がどう見えるか（代替：砂・花瓶。中身は表示されないはず）
- [ ] トーチフラワー・ピンクの花びらがどう見えるか（代替：タンポポ・ボタン）
- [ ] スニッファーの卵・スニッファーがどう見えるか（代替：カメの卵・ブタ）
- [ ] ブラシ・鍛冶型テンプレート・陶片がどう見えるか（代替：棒・鉄／ネザライトインゴット・レンガ）
- [ ] チェリーの看板・ボートがどう見えるか（代替：樫の看板・樫のボート）
- [ ] 鍛冶台を開ける（3スロットが旧2スロットに写る。鍛冶型を入れたときの挙動）
- [ ] 桜の木の音・スニッファーの声がどう聞こえるか（代替：木の音・ブタの声）
- [ ] 桜の葉のパーティクルがどう見えるか（代替：white_ash）
- [ ] 2人目が参加したとき、相手が見えるか

## 4. チェックリスト（1.19.3クライアント → 内部763サーバー）

`minecraft-version` を `1.20.1` にして起動し、1.19.3クライアントで接続する。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 桜のバイオーム（cherry_grove）がどう見えるか（代替：meadow。バイオーム63→64の変換）
- [ ] 1.20.1の新しいブロック・アイテムがどう見えるか（上の3と同じ代替）
- [ ] 2人目が参加したとき、相手が見えるか

## 5. チェックリスト（1.19.4／1.20.1クライアント → 内部761サーバー）

`minecraft-version` を `1.19.3` にして起動し、1.19.4／1.20.1クライアントで接続する。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 1.19.3の世界が正しく見えるか（ブロック状態・バイオームの逆向き変換）
- [ ] 2人目が参加したとき、相手が見えるか

## 6. 結果の記録

| 日付 | クライアント | サーバー | 結果 | 備考 |
| --- | --- | --- | --- | --- |
| （未実施） | 1.19.3 | 1.19.3（761） | — | — |
| （未実施） | 1.19.3 | 1.19.4（762） | — | — |
| （未実施） | 1.19.3 | 1.20.1（763） | — | — |
| （未実施） | 1.19.4（762） | 1.19.3（761） | — | — |
| （未実施） | 1.20.1（763） | 1.19.3（761） | — | — |
| （未実施） | 1.19.3 | 1.19.3（761）2人同時 | — | 最重要：相手が見えるか |

結果を報告するときは、通った項目・通らなかった項目に加え、画面の文言・
ログの文言も一緒に報告すること（特に切断された場合のメッセージと、
相手が見えなかった場合の様子）。
