* # Gitlet - A Git-like Version Control System

    This project is an implementation of a simplified version of the Git version control system, named Gitlet. It was developed as a practical exercise to understand the core concepts of version control, file system manipulation, object serialization, and graph algorithms (like finding the Lowest Common Ancestor).

    **Developer**: [Lyrine Yang](https://lyrineyang.github.io/)

    ## Project Goal

    Gitlet aims to simulate some of the core functionalities of the real Git, including but not limited to:

    * Initializing a repository (`init`)
    * Adding files to the staging area (`add`)
    * Committing changes (`commit`)
    * Viewing commit history (`log`, `global-log`)
    * Finding specific commit messages (`find`)
    * Checking repository status (`status`)
    * Checking out files or commits (`checkout`)
    * Branch management (`branch`, `rm-branch`)
    * Resetting to a specific commit (`reset`)
    * Merging branches (`merge`)

    ---
    ## Design Philosophy and Core Implementation

    ### 1. The `.gitlet` Directory: The Heart of Gitlet

    All metadata, objects, and state for Gitlet are centrally stored in a top-level hidden directory named `.gitlet`. This design is inspired by Git's `.git` directory and provides a unified root for all operations.

    The internal structure primarily includes:

    * **`objects/`**: Stores Gitlet's core data objects.
        * **`commits/`**: Contains all Commit objects. Each serialized commit object is stored in a file named after its SHA-1 hash.
        * **`blobs/`**: Contains all tracked file content snapshots (Blob objects), also stored in files named after their content's SHA-1 hash.
    * **`branches/`**: Stores branch information. Each file represents a branch, with the filename being the branch name and the file content being the SHA-1 ID of the branch's latest commit.
    * **`HEAD`**: A special file whose content points to the name of the currently active branch (e.g., "master"), thereby indirectly pointing to that branch's latest commit.
    * **`index`**: The staging area file. It stores a serialized map representing the state of the staging area (filename -> Blob ID, or filename -> deletion marker).

    ---
    ### 2. Core Object Model

    * **Blob (`gitlet.Blob`)**:
        * A Blob object is concerned only with the pure binary data (`byte[]`) of a file's content, ensuring support for any file type.
        * Its identity is determined by the SHA-1 hash of its content, which also serves as its filename in the `objects/blobs/` directory.

    * **Commit (`gitlet.Commit`)**:
        * Represents a snapshot of the project at a specific point in time.
        * Contains metadata: commit message, timestamp (as a formatted string), the SHA-1 ID(s) of parent commit(s) (a primary parent and an optional second parent for merges).
        * Its core is a `nameIDMap` (a `HashMap<String, String>` in the design log, likely a `TreeMap` in implementation for ordering as suggested in the README draft), mapping all tracked filenames (including relative paths) to their corresponding Blob object's SHA-1 ID.
        * A Commit's ID is also the SHA-1 hash of its serialized content (metadata and `nameIDMap`).

    ---
    ### 3. Staging Area (`index` file)

    * Acts as a bridge between the working directory and the commit history.
    * Represented by a `HashMap<String, String>` serialized into the `.gitlet/index` file.
        * Keys are filenames.
        * Values can be the Blob ID for added/modified files or a special deletion marker string (e.g., `DELETE_MARKER`) for files marked for removal.
    * The `add` command creates a Blob from the file's current content and updates the staging area.
    * The `rm` command marks a file for deletion in the staging area and removes it from the working directory (if tracked).
    * The `commit` command creates a new Commit based on the staging area's state and then clears the staging area.

    ---
    ### 4. Branching and Merging

    * **Branch**: Essentially a movable pointer to a specific commit, implemented as a file storing a commit ID. The `HEAD` file indicates the current working branch.
    * **Merge**:
        * Based on a three-way merge: the latest commit of the current branch (H), the latest commit of the given branch (G), and their Lowest Common Ancestor (S, or split point).
        * **Lowest Common Ancestor (LCA) Finding**: This is crucial. The initial LCA implementation had a flaw where it only considered the first parent of merge commits, potentially leading to an incorrect (too old) split point. The algorithm was evolved to correctly traverse all parent paths of merge commits to accurately locate the split point. This often involves collecting all ancestors of one branch head into a set, then traversing upwards from the other branch head, with the first ancestor found in the set being a candidate. To ensure the "closest" LCA, all common ancestors might be collected, and the one with the latest timestamp chosen.
        * **File Status Determination & Handling**: The state of files in S, H, and G determines the merged file's state (e.g., kept, added, deleted, conflicted).
        * **Conflict Resolution**: Conflicts arise if a file is changed differently in H and G relative to S, or if one branch modifies a file and another deletes it. Conflicted files are marked (e.g., `<<<<<<< HEAD...`) and their conflicted content is staged for manual resolution.
        * A new merge commit with two parents (pointing to H and G) is created upon successful merge.

    ---
    ### 5. Persistence and Utilities

    * All core objects (Commits, Blobs) and the staging area state are persisted using Java's serialization mechanism.
    * `Utils.java` provides helper methods for SHA-1 hashing, file I/O, object serialization/deserialization, etc..
    * The `restrictedDelete` method in `Utils.java` was designed for safe deletion of workspace files, ensuring operations occur within a Gitlet repository. It was initially flawed for subdirectory files but was later corrected.

    ---
    ## Overview of Main Command Implementations

    * **`init`**: Creates the `.gitlet` directory structure and an initial commit.
    * **`add [file]`**: Creates a Blob from the file content, stores it, and maps the filename to the Blob ID in the `index`. If the file content is identical to the version in the current commit, it's removed from staging if present.
    * **`commit [message]`**: Creates a new Commit object based on the `index` and the parent commit. Clears staging upon success.
    * **`rm [file]`**: Unstages the file if staged. If tracked in the current commit, stages it for removal and deletes it from the working directory.
    * **`log`**: Displays commit history starting from `HEAD`, traversing parent commits.
    * **`global-log`**: Displays the history of all commits.
    * **`find [commit-message]`**: Prints IDs of all commits with the given message.
    * **`status`**: Shows current branch, staged files, removed files, modifications not staged, and untracked files.
    * **`checkout -- [filename]`**: Restores the file in the working directory to its version in the `HEAD` commit.
    * **`checkout [commit-id] -- [filename]`**: Restores the file to its version in the specified commit (supports abbreviated IDs).
    * **`checkout [branch-name]`**: Switches to the specified branch, updating the working directory, `HEAD`, and clearing staging. Checks for untracked file overwrites.
    * **`branch [branch-name]`**: Creates a new branch pointing to the current `HEAD` commit.
    * **`rm-branch [branch-name]`**: Deletes the specified branch (cannot delete the current branch).
    * **`reset [commit-id]`**: Resets the current branch to the specified commit, updates the working directory, and clears staging. Checks for untracked file overwrites.
    * **`merge [branch-name]`**: Merges the specified branch into the current branch, involving LCA finding, file comparisons, conflict handling, and creating a merge commit.

    ---
    ## Development Journey and Challenges

    Building a version control system from scratch was a challenging yet rewarding endeavor, especially without prior experience in developing such systems.

    * **Conceptual Mapping**: Translating abstract Git concepts (commits, branches, staging area, HEAD, content-addressing) into concrete file system operations and Java object models was a primary hurdle.
    * **Data Structure Design**: Careful consideration was given to representing commit relationships, file snapshots within commits, and the staging area's state.
    * **Object Persistence**: Ensuring that crucial data (Commits, Blobs, Index) could be reliably saved and loaded across different program invocations was fundamental.
    * **SHA-1 Hashing**: Understanding and correctly applying SHA-1 hashing for unique identification of Commits and Blobs was key to implementing content-addressing.
    * **Branching and Merging Logic**: The `merge` command was undoubtedly the most complex part. Correctly implementing three-way merging, especially the accurate finding of the Lowest Common Ancestor (LCA), and meticulously handling various file conflict scenarios, took significant design, debugging, and refinement. The understanding and implementation of the LCA algorithm evolved from a simplified version to a more complete one capable of handling merge commits.
    * **Edge Case Handling**: Addressing various potential user errors and edge cases (empty commits, file overwrites, non-existent branches, etc.) was crucial for system robustness.
    * **Debugging and Testing**: For a system with extensive file operations and state changes, thorough debugging and comprehensive testing were vital.

    This project not only deepened my understanding of how version control systems work but also honed my skills in complex system design, problem decomposition, and debugging.

    ---
    ## How to Run (Example)

    *(You'll need to adapt this section based on your `Main.java`'s actual usage; this is a generic example.)*

    1.  Compile all `.java` files:
        ```bash
        javac gitlet/*.java
        ```
    2.  Run Gitlet commands:
        ```bash
        java gitlet.Main <command> [operands]
        ```
        For example:
        ```bash
        java gitlet.Main init
        java gitlet.Main add myfile.txt
        java gitlet.Main commit -m "Add myfile.txt"
        java gitlet.Main log
        ```

    ---
    ## Acknowledgements 

    Thanks to my collaborator [Yuteng Huang](https://github.com/isHarryh) who leads me to be a better programmer. Thanks to Jiang Zhuo and bzWang who brings me a lot of fun. Thanks to professor Josh Hug and all CS61b's TAs and profs bring me this excellent project.

    ---
