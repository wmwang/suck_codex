# 系統架構說明（LegacyLinker）

## 目的與範圍
LegacyLinker 用於掃描 VB6 專案（`.vbp`）與程式碼檔（`.frm`、`.bas`、`.cls`），萃取跨系統的執行檔呼叫關係，並輸出可瀏覽的 MkDocs 文件網站。本文件描述工具在「分析與產出 Wiki」流程中的核心元件與資料流向。

## 高階架構

```mermaid
flowchart TB
    CLI[CLI 入口 - Main] --> Scanner[ProjectScanner 掃描 .vbp]
    Scanner --> Parser[解析專案結構 (Forms/Modules/Classes)]
    Parser --> DepScan[依賴掃描 (Regex + AST)]
    DepScan --> Model[VbProject / ProjectDependency]
    Model --> Generator[MarkdownGenerator 產生文件]
    Generator --> MkDocs[mkdocs.yml + docs/]
```

## 核心元件

### 1) CLI 入口
- **`Main`** 是程式進入點，接收輸入目錄與輸出目錄參數，並啟動掃描與文件生成流程。它會在每次執行前重置 AST 分析報告，並將掃描結果交給 `MarkdownGenerator` 產出 MkDocs。 【F:legacy_linker/src/main/java/com/legacy/linker/Main.java†L1-L45】

### 2) 專案掃描與解析
- **`ProjectScanner`** 會遞迴掃描指定根目錄，尋找所有 `.vbp` 檔，解析專案名稱、輸出 EXE 名稱與相關檔案（Forms/Modules/Classes）。 【F:legacy_linker/src/main/java/com/legacy/linker/scanner/ProjectScanner.java†L1-L64】
- 解析後會針對所有來源檔進行依賴分析，並建構 `VbProject` 物件，內含專案基本資訊與 `ProjectDependency` 清單。 【F:legacy_linker/src/main/java/com/legacy/linker/scanner/ProjectScanner.java†L66-L97】

### 3) 依賴分析（Regex + AST）
- **Regex 掃描**：在每個來源檔的逐行掃描中，偵測 `.exe`、`shell`、`createobject` 等關鍵字，並嘗試擷取 EXE 名稱。 【F:legacy_linker/src/main/java/com/legacy/linker/scanner/ProjectScanner.java†L99-L152】
- **AST 分析**：`AstAnalyzer` 會先進行文字清理與轉碼，再用 VB6 Parser 解析語法樹，於 Procedure 範圍內檢測 `Shell` 與 `CreateObject` 呼叫。 【F:legacy_linker/src/main/java/com/legacy/linker/scanner/AstAnalyzer.java†L1-L246】
- **AST 結果彙整**：分析結果會記錄在 `AstAnalysisReport` 中並輸出摘要文件。 【F:legacy_linker/src/main/java/com/legacy/linker/generator/MarkdownGenerator.java†L26-L155】

### 4) 文件生成
- **`MarkdownGenerator`** 會產出：
  - `mkdocs.yml`（網站設定與導覽）
  - `docs/index.md`（首頁總覽）
  - `docs/dependencies.md`（全域依賴圖）
  - `docs/ast_summary.md`（AST 分析摘要）
  - 專案頁（每個 `.vbp` 一頁）與原始碼頁（逐行檢視）
  【F:legacy_linker/src/main/java/com/legacy/linker/generator/MarkdownGenerator.java†L26-L336】

## 資料模型

| 模型 | 目的 | 主要內容 |
| --- | --- | --- |
| `VbProject` | 專案資訊載體 | 專案名稱、`exe` 名稱、表單/模組/類別路徑、依賴清單 |【F:legacy_linker/src/main/java/com/legacy/linker/model/VbProject.java†L1-L26】|
| `ProjectDependency` | 依賴關係紀錄 | 目標 exe、來源檔案、行號、原始內容、判斷上下文 |【F:legacy_linker/src/main/java/com/legacy/linker/model/ProjectDependency.java†L1-L18】|

## 主要輸出
- **MkDocs 網站結構**：包含首頁、專案頁、依賴圖與 AST 摘要，用於快速瀏覽專案資訊。 【F:legacy_linker/src/main/java/com/legacy/linker/generator/MarkdownGenerator.java†L26-L336】
- **依賴圖（Mermaid）**：以節點與邊描述專案間呼叫關係。 【F:legacy_linker/src/main/java/com/legacy/linker/generator/MarkdownGenerator.java†L214-L254】

## 擴充建議
- 新增依賴類型（例如 DLL、COM 物件）可在 `ProjectScanner` Regex 區與 `AstAnalyzer` 的判斷邏輯中擴展規則。 【F:legacy_linker/src/main/java/com/legacy/linker/scanner/ProjectScanner.java†L99-L152】【F:legacy_linker/src/main/java/com/legacy/linker/scanner/AstAnalyzer.java†L210-L246】
