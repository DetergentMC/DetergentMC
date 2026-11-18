# DetergentMC 公式サイト（web/）

DetergentMC の公式ウェブサイトです。静的 HTML だけで作られています。
デザイン（`style.css`・`favicon.svg`）は既存の DetergentMC/DetergentWeb から流用し、
文章はすべて今のプロジェクトの事実に合わせて書き直しました。

## ページ一覧

| ファイル | 内容 |
| --- | --- |
| `index.html` | DetergentMC の紹介・現在の到達点・目指すところ・正直な話 |
| `index-en.html` | index.html の英語版 |
| `versions.html` | 対応バージョン一覧（Java 35 プロトコル＋ Bedrock 63 プロトコル＋対応マトリクス＋今後の予定） |
| `versions-en.html` | versions.html の英語版（対応マトリクスは英語版の画像をメイン表示） |
| `matrix.svg` | 対応マトリクス表の画像版（SVG。versions.html から表示） |
| `matrix.png` | 対応マトリクス表の画像版（PNG。ダウンロード用） |
| `matrix-en.svg` | 対応マトリクス表の画像版・英語版（SVG。versions.html から表示） |
| `matrix-en.png` | 対応マトリクス表の画像版・英語版（PNG。ダウンロード用） |
| `how.html` | 仕組み（3つのポート、バージョン間アダプター、Bedrock 対応） |
| `how-en.html` | how.html の英語版 |
| `products.html` | DetergentMC 本体とプラグイン「バブル」の説明 |
| `products-en.html` | products.html の英語版 |
| `roadmap.html` | これまでの道のりと今後の予定 |
| `roadmap-en.html` | roadmap.html の英語版 |
| `style.css` | デザイン（DetergentWeb から流用） |
| `favicon.svg` | ファビコン（DetergentWeb から流用） |

## ローカルで確認する方法

```sh
cd web
python3 -m http.server 8000
```

ブラウザで http://localhost:8000/ を開いてください。

## 公開する方法（案内のみ）

GitHub Pages で公開する場合は、このリポジトリの設定で公開元（Build and deployment）
を「GitHub Actions」にし、`.github/workflows/pages.yml` が `web/` をデプロイします。
main への push（`web/` かワークフロー自体の変更）で自動デプロイされます。
公開 URL は https://detergentmc.github.io/DetergentMC/ です。公開前には
各ページの数字（プロトコル数・テスト件数など）が今の事実と合っているか確認してください。

## 正直な話

このサイトの数字はプロトコルレベルの検証にもとづくものです。
本物のゲームクライアントでの確認は、まだどの組み合わせも行っていません。
