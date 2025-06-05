# Gitlet 项目：开发者日志与设计思路演进

**开发者**: Lyrine Yang
**项目目标**: 实现一个简化版的 Git 版本控制系统——Gitlet。
**核心挑战**: 在没有现有版本控制系统经验的情况下，从零开始设计和实现其核心数据结构、对象持久化、分支管理和命令逻辑。

## 一、核心概念奠基与前期架构设计

在项目初期，最大的挑战是如何将 Git 的抽象概念（如提交、分支、暂存区）映射到具体的文件系统操作和 Java 对象模型。这部分的思考直接决定了后续所有命令实现的基础。

### 1. `.gitlet` 目录：Gitlet 的宇宙中心

* **核心决策**：所有 Gitlet 的元数据、对象和状态都必须集中存储在一个名为 `.gitlet` 的顶级隐藏目录中。这借鉴了真实 Git 的 `.git` 目录设计，为所有操作提供了一个统一的根。
* **面临的问题**：如何在 `.gitlet` 内部组织信息？哪些信息需要持久化？
* **设计思路与演进**：
    * **对象存储 (`objects/`)**：意识到 Commit 和文件内容（Blobs）是核心数据，需要一个地方统一存储。设计了 `objects/` 目录。
        * **进一步细化**：为了更清晰地区分不同类型的对象，并简化查找，决定在 `objects/` 下再创建子目录 `commits/` 和 `blobs/`。这比真实 Git 将所有对象混用哈希前缀分桶的方式更易于本项目实现。
    * **分支表示 (`branches/` 或 `refs/heads/`)**：如何表示分支？一个分支本质上是一个指向特定 Commit 的可变指针。
        * **初步想法**：每个分支对应一个文件，文件名是分支名，文件内容是该分支最新 Commit 的 SHA-1 ID。这些文件可以放在 `.gitlet/branches/` 目录下。
        * **深入思考 (受真实Git启发)**：了解到真实 Git 使用 `refs/heads/` 结构，其父目录 `refs/` 用于更广泛的“引用”管理（如标签 `refs/tags/`）。对于 Gitlet，虽然不一定需要实现所有引用类型，但理解这个设计有助于建立更具扩展性的概念模型。最终，为简化起见，决定直接使用 `branches/` 目录，这对于项目要求是足够且清晰的。
    * **当前活动分支 (`HEAD` 文件)**：如何知道当前工作在哪条分支上？
        * **初步想法**：`HEAD` 文件直接存储当前活动分支最新 Commit 的 SHA-1 ID。
        * **演进与优化**：意识到这种方式在切换分支时，需要同时更新 `HEAD` 和分支文件。如果 `HEAD` 文件存储的是**当前活动分支的名称**（例如 `"master"` 或 `"dev"`），那么 `HEAD` 就成了一个指向分支文件的“间接指针”。当切换分支时，只需修改 `HEAD` 文件内容；当在新分支上提交时，只需修改对应的分支文件内容。这个设计更解耦，也更接近真实 Git 的 `ref: refs/heads/master` 机制。
    * **暂存区 (`index` 文件)**：`add` 命令需要一个地方记录“下次要提交的内容”。
        * **核心问题**：暂存区是临时的还是需要持久化的？它应该存储什么信息？
        * **设计决策**：暂存区状态必须在 Gitlet 命令执行之间持久化。因此，需要一个文件，最初命名为 `stagingArea`，后根据 Git 术语和简洁性考虑，确定为 `index` 文件。其内容将是暂存区数据结构（一个 Map）的序列化形式。
* **辅助方法封装**：为了保证 `init` 命令创建目录结构的原子性和代码的整洁，设计了 `setupPersistence()` 辅助方法，集中处理所有初始目录和文件的创建。

### 2. `Blob` 对象：文件内容的纯粹载体

* **核心职责**：Gitlet 需要存储文件的实际内容。`Blob` 对象为此而生。
* **设计思考：`String` vs. `byte[]`**
    * **初步想法**：文件内容可以用 `String` 表示吗？
    * **深入分析与引导**：Gitlet 需要能处理任意类型的文件，包括图片、编译后的代码等二进制文件。`String` 主要用于文本，如果用 `String` 存储二进制内容，会因字符编码问题导致数据损坏或信息丢失。
    * **最终设计**：`Blob` 类 的核心是一个 `private byte[] content;` 成员。`byte[]` 是 Java 中表示任意二进制数据的最直接、最通用的方式，能确保内容的保真性。这与 `Utils.readContents(File)` 返回 `byte[]` 以及 `Utils.writeContents(File, byte[])` 能够直接处理 `byte[]` 形成了良好配合。
* **`Blob` 的身份与元数据**：
    * **思考**：`Blob` 对象是否需要知道自己的文件名、路径或哈希ID？
    * **设计决策**：`Blob` 只关心纯粹的**内容**。文件名和路径是目录结构（由 `Commit` 内部的 Map 或 `Tree` 对象维护）的一部分。Blob 的 SHA-1 哈希 ID 是根据其内容计算出来的，是其外部的“身份牌”，通常作为其在 `objects/blobs/` 目录下的文件名，而不应作为 `Blob` 对象自身的成员变量。这符合“内容寻址”的核心思想。

### 3. `Commit` 对象：历史快照的忠实记录

* **核心职责**：代表项目在某个特定时间点的完整快照，并形成历史链。
* **元数据设计**：
    * **Message (`commitMessage`)**：用户提供的提交信息，`String` 类型。
    * **Timestamp (`timeStamp`)**：
        * **初步想法**：使用 `java.util.Date` 对象。
        * **演进**：考虑到项目规范对 `log` 命令输出格式有特定要求，并且为了序列化和跨平台的一致性（以及可能的简化），决定存储由 `SimpleDateFormat` 按 `"EEE MMM d HH:mm:ss yyyy Z"` 格式（使用 `Locale.US`）格式化后的 `String` 类型时间戳。实现了 `getTimeStampString()` 辅助方法。
    * **Parent Pointer(s)**：
        * `parentID` (`String`)：存储父 Commit 的 SHA-1 ID。对于初始 Commit，此值为 `null`。
        * (针对 Merge) `secondParentID` (`String`)：为 `merge` 命令引入，存储第二个父 Commit 的 SHA-1 ID。普通 Commit 此值为 `null`。
    * **文件快照 (`nameIDMap`)**：
        * **核心问题**：如何表示一个 Commit 跟踪的所有文件及其特定版本？
        * **设计决策**：使用 `HashMap<String, String>`。其中，Key 是文件名（包含相对路径，如 `src/Main.java`），Value 是该文件内容对应的 Blob 对象的 SHA-1 ID。
        * **不变性**：一旦 Commit 创建，其 `nameIDMap` 就代表了一个固定的历史快照，不应再被修改。

* **Commit ID 生成 (`sha1`)**：
    * **核心原则**：Commit ID 是其所有元数据和文件快照内容的唯一标识。
    * **实现方式**：将 `Commit` 对象（包含其 message, timestamp, parent ID(s), 和 `nameIDMap` 的一种稳定表示）序列化成 `byte[]`，然后对这个字节数组计算 SHA-1 哈希值。最初在调用 `sha1` 时曾直接传入 `Commit` 对象，后通过分析 `Utils.sha1` 的参数要求（`byte[]` 或 `String`），修正为先调用 `Utils.serialize(commitObject)` 再进行哈希。

* **初始 Commit 的特殊性**：
    * 在 `init` 命令成功执行后自动创建。
    * Message: `"initial commit"`。
    * Timestamp: UNIX 纪元 (`new Date(0L)`)。
    * `parentID`: `null`。
    * `nameIDMap`: 空的 `HashMap`。

### 4. 暂存区 (`Staging Area` / `index` 文件)：连接工作目录与提交历史的桥梁

* **核心作用**：允许用户选择性地将工作目录中的更改组织起来，作为下一次提交的内容。
* **数据结构选择**：
    * **初期思考**：如何表示“已添加但未提交”的文件？如何表示“已从跟踪中移除但未提交”的文件？
    * **设计方案对比与演进**：
        1.  **双集合方案**：一个 `Map<String, String> filesToAddUpdate` (文件名 -> Blob ID) 和一个 `Set<String> filesToRemove` (文件名)。
        2.  **单 Map + 特殊标记方案**：使用一个 `HashMap<String, String> stagingMap`。
            * 文件名 -> Blob ID：表示文件被添加或修改。
            * 文件名 -> `DELETE_MARKER` (一个特殊字符串常量，如 `"DELETE_FILE"`)：表示文件被标记为删除。
        * **决策**：选择了方案B。这种方式将所有暂存信息统一在一个数据结构中，便于统一序列化到 `index` 文件。但要求在使用该 Map 时，需要通过检查 Value 是否等于 `DELETE_MARKER` 来区分是添加/修改还是删除。

* **持久化**：
    * 暂存区的状态必须在 Gitlet 命令执行之间保持。
    * 将这个 `stagingMap` 对象序列化后存入 `.gitlet/index` 文件。
    * 每次 `add` 或 `rm` 操作后，都需要**更新** `index` 文件。
    * 每次 `commit` 操作成功后，需要**清空** `index` 文件（即写入一个空的 `HashMap`）。

* **`add` 命令与暂存区的交互**：
    * `add` 将工作目录文件的当前内容生成一个新的 Blob（如果该内容对应的 Blob 不存在），获取其 Blob ID。
    * 然后将 `(fileName, blobID)` 存入（或更新）暂存区 Map。
    * **关键设计点 (通过引导发现与确认)**：如果被 `add` 的文件，其当前工作目录内容与 `HEAD` Commit 中该文件的内容完全相同，则：
        * 如果该文件之前在暂存区中（可能因为中间有过修改又改了回来），应将其从暂存区移除。
        * 否则，不进行任何暂存操作。
        这避免了对未改变文件进行不必要的暂存。

* **`commit` 命令与暂存区的交互**：
    * **关键设计点 (通过引导发现与确认)**：当创建一个新的 Commit 时，其 `nameIDMap` (文件快照) **不能**简单地直接引用暂存区的 Map 对象。因为暂存区 Map 是可变的，而 Commit 的内容必须是不可变的。
    * **正确流程**：
        1.  创建一个基于父 Commit 的 `nameIDMap` 的**浅拷贝**作为新 Commit 的 `nameIDMap` 基础。
        2.  遍历当前的暂存区 Map。
        3.  如果暂存区条目是添加/更新，则将 `(fileName, blobID)` `put` 到新 Commit 的 Map 副本中（会覆盖从父 Commit 继承来的同名文件）。
        4.  如果暂存区条目是删除标记 (`DELETE_MARKER`)，则从新 Commit 的 Map 副本中 `remove(fileName)`。
    * 这个过程确保了新 Commit 的文件列表是父 Commit 状态与暂存区变更正确合并的结果。

---
## 二、核心命令逻辑设计要点 (概要)

在前期核心数据结构和交互逻辑奠定后，各个命令的实现虽然仍有细节，但思路会更加清晰。

* **`rm` 命令**：负责更新暂存区（标记文件为 `DELETE_MARKER`）并从工作目录删除被跟踪的文件。不直接修改 Commit。
* **`log` 命令**：从 `HEAD` 指向的 Commit 开始，通过 `parentID` (和 `secondParentID` 对于 merge commit 的显示) 递归回溯并打印历史。ID 的获取和一致性是关键。
* **`checkout` 命令 (三种形式)**：
    * **参数分发**：根据参数数量和 `"--"` 分隔符准确分发。
    * **核心操作**：从指定的 Commit (HEAD、特定ID或分支头) 中获取 Blob ID，然后用 `Utils.writeContents` 将 Blob 内容恢复到工作目录。
    * **`checkout [branch]`**：涉及工作目录清理（删除只在当前分支跟踪的文件）、从目标分支恢复文件、更新 `HEAD`、清空暂存区。未跟踪文件覆盖检查是重要错误处理。
    * **Commit ID 缩写**：`checkout [commitID] -- file` 需要支持缩写 ID 查找，设计了 `getCommitByID` (原名 `findFullCommitIdFromShort`) 来处理，需要注意歧义性问题（尽管项目规范可能不严格要求处理歧义）。
* **`find`, `global-log`**：都涉及到遍历 `.gitlet/objects/commits/` 目录，加载所有 Commit 对象进行判断或打印。
* **`status` 命令**：准确地区分“Staged Files”（暂存区中非删除标记）、“Removed Files”（暂存区中为删除标记）、“Untracked Files”（存在于CWD，但既不在HEAD Commit中，也不以任何形式在暂存区中）等。`getUntrackedFileList()` 的逻辑在与暂存区状态协调后变得准确。
* **`branch`, `rm-branch`**: 直接操作 `branches/` 目录下的文件和 `HEAD` 文件。
* **`reset`**: 将当前分支指向指定的 commit，用该 commit 的快照更新工作目录，并清空暂存区。
* **`merge` 命令 (最复杂)**：
    * **核心**：三方合并，基于当前分支(H)、给定分支(G)和它们的最近共同祖先(S)。
    * **预检查**：暂存区干净、分支有效、非自身合并、无未跟踪文件覆盖。
    * **特殊合并**：快进、给定分支是祖先。
    * **文件分类与处理**：通过遍历所有相关文件，对每个文件分析其在S、H、G三方的状态（是否存在、Blob ID是否相同），然后根据规范的约8条规则执行操作（检出、暂存、删除、标记冲突）。
    * 使用 `Objects.equals()` 安全比较可能为 `null` 的 blobID。
    * **冲突处理**：
        * 准确识别所有冲突场景。
        * 生成符合规范的冲突文件内容（`<<<<<<< HEAD...`）。
        * 无论冲突文件之前是否存在于CWD，最终CWD中都应有一个包含冲突标记内容的文件。
        * 将冲突文件作为一个新的 blob 内容进行**暂存**。
    * **Merge Commit**：自动创建一个新的 commit，其 message 符合规范，并且具有**两个父ID**。`Commit` 类需要有相应的构造函数支持。
    * **暂存区**：所有文件操作完成后，将最终暂存区状态写回 `index`，**然后**才执行 merge commit，最后清空暂存区。

---
## 三、性能与优化思考 (概要)

* **Commit ID 缩写查找优化**：曾思考过使用类似真实 Git 的两级“分桶”目录结构来优化 `objects/` 下对象的查找，但对于本项目规模，线性扫描 `COMMITS_DIR` 是可接受的。
* **`Utils.plainFilenamesIn` 返回 `List`**：分析了其返回排序列表的职责，调用者可按需转为 `HashSet` 以获取快速查找。

---

