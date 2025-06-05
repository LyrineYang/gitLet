package gitlet;

import static gitlet.Utils.*;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/** Represents a gitlet repository.
 * Includes file path in gitlet directory and implements of gitlet commands by
 *  store information in files.
 *  does at a high level.
 *  .gitlet/
 *        - objects/
 *            - commits/
 *                - ...(files of commits)
 *            - blobs/
 *                - ...(files of blobs)
 *        - branches/
 *            - master
 *            - ...(other branches)
 *        - HEAD
 *        - index/
 *
 *  @author Lyrine Yang
 */
public class Repository {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /**
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */
    private static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    private static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    private static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    private static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    private static final File INDEX_FILE = join(GITLET_DIR, "index");
    private static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    private static final File MASTER_FILE = join(BRANCHES_DIR, "master");
    private static final String DELETE_MARKER = "DELETE_FILE";

    /**
     * to create the gitlet directory and the file structure
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system"
                    + " already exists in the current directory.");
            return;
        }
        setupPersistence();
        /* create the first default commit */
        Date epochTime = new Date(0L);
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        Commit initialCommit = new Commit(null, "initial commit", formatter.format(epochTime));
        String initialCommitID = sha1(serialize(initialCommit));
        File initialCommitFile = join(COMMITS_DIR, initialCommitID);
        writeObject(initialCommitFile, initialCommit);

        /* build up the branch structure by create branch file and the HEAD file */
        /* make branch file hold the branch new commitID */
        writeContents(MASTER_FILE, initialCommitID);
        /* make HEAD file hold the working branch information */
        writeContents(HEAD_FILE, "master");
    }

    /**
     * build up the gitlet directory structure and store new hashmap in index file
     */
    private static void setupPersistence() {
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        BRANCHES_DIR.mkdir();
        try {
            HEAD_FILE.createNewFile();
            INDEX_FILE.createNewFile();
            HashMap<String, String> stagingArea = new HashMap<>();
            writeObject(INDEX_FILE, stagingArea);
            MASTER_FILE.createNewFile();
        } catch (IOException excp) {
            throw new RuntimeException(excp);
        }
    }

    /**
     * add the file need to add to blobs directory and add the key-value to index
     */
    public static void add(String fileName) {
        File fileToAdd = join(CWD, fileName);
        if (!fileToAdd.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        /* get the content of the file needed to add */
        byte[] fileToAddContent = readContents(fileToAdd);
        String blobID = sha1(fileToAddContent);

        /* inverse serialize the index map from index file */
        HashMap<String, String> stagingArea = readObject(INDEX_FILE, HashMap.class);
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));

        /* check whether current working version of the file is identical */
        if (headCommit.getNameIDMap().containsKey(fileName)
                && headCommit.getNameIDMap().get(fileName).equals(blobID)) {
            if (stagingArea.containsKey(fileName)) {
                stagingArea.remove(fileName);
                writeObject(INDEX_FILE, stagingArea);
            }
            return;
        }
        /* add addFile content to blobs directory */
        if (!join(BLOBS_DIR, blobID).exists()) {
            Blob addBlob = new Blob(fileToAddContent);
            writeObject(join(BLOBS_DIR, blobID), addBlob);
        }
        /* add the addFile blob pointer to index map */
        stagingArea.put(fileName, blobID);
        writeObject(INDEX_FILE, stagingArea);
    }

    /**
     * get commit object according its commitID
     * by go through commits directory(maybe shortened)
     */
    private static Commit getCommitByID(String commitID) {
        List<String> commitsIDList = plainFilenamesIn(COMMITS_DIR);
        for (String commitsID : commitsIDList) {
            if (commitsID.startsWith(commitID)) {
                File commitFile = join(COMMITS_DIR, commitsID);
                Commit commit = readObject(commitFile, Commit.class);
                return commit;
            }
        }
        return null;
    }

    /**
     * get the given branch head commit object
     */
    private static Commit getBranchHeadCommit(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        return getCommitByID(readContentsAsString(branchFile));
    }

    public static void commit(String commitMessage) {
        commit(commitMessage, null);
    }

    private static void commit(String commitMessage, String secondParentCommitID) {
        if (commitMessage.trim().isEmpty()) {
            System.out.println("Please enter a commit message.");
            return;
        }
        /* check if there is file in staging area to commit */
        HashMap<String, String> stagingAreaMap = readObject(INDEX_FILE, HashMap.class);
        if (stagingAreaMap.isEmpty() && secondParentCommitID == null) {
            System.out.println("No changes added to the commit.");
            return;
        }
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        String headCommitID = sha1(serialize(headCommit));
        Commit newCommit = new Commit(headCommitID, commitMessage,
                getTimeStampString(), secondParentCommitID);

        /* load the headCommit map and put the staging area: the stagingAreaMap */
        newCommit.loadParentCommitMap(headCommit.getNameIDMap());
        for (HashMap.Entry<String, String> entry : stagingAreaMap.entrySet()) {
            String fileName = entry.getKey();
            String blobID = entry.getValue();
            /* if there are file need to delete,
            remove it from nameIDMap to cancel tracking of it */
            if (blobID.equals(DELETE_MARKER)) {
                newCommit.getNameIDMap().remove(fileName);
            } else {
                newCommit.getNameIDMap().put(fileName, blobID);
            }
        }

        /* build the newCommit File in Commits directory to save it */
        String newCommitID = sha1(serialize(newCommit));
        File newCommitFile = join(COMMITS_DIR, newCommitID);
        writeObject(newCommitFile, newCommit);

        /* clean the stagingArea */
        writeObject(INDEX_FILE, new HashMap<String, String>());

        /* make the HEAD pointer to point at the new commit */
        writeContents(join(BRANCHES_DIR, readContentsAsString(HEAD_FILE)), newCommitID);

    }

    private static String getTimeStampString() {
        Date timeStamp = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return formatter.format(timeStamp);
    }

    private static Date parseTimeStamp(String timeStampString) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        try {
            return formatter.parse(timeStampString);
        } catch (ParseException e) {
            return null;
        }
    }

    public static void remove(String fileName) {
        /* get the head commit and staging area map */
        boolean stagingAreaChanged = false;
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        HashMap<String, String> stagingArea = readObject(INDEX_FILE, HashMap.class);
        if (stagingArea.containsKey(fileName) && !stagingArea.get(fileName).equals(DELETE_MARKER)) {
            stagingArea.remove(fileName);
            stagingAreaChanged = true;
        }
        if (headCommit.getNameIDMap().containsKey(fileName)) {
            stagingArea.put(fileName, DELETE_MARKER);
            stagingAreaChanged = true;
            File cwdFileToRm = join(CWD, fileName);
            if (cwdFileToRm.exists()) {
                restrictedDelete(cwdFileToRm);
            }
        }
        if (!stagingAreaChanged) {
            System.out.println("No reason to remove the file.");
            return;
        }
        writeObject(INDEX_FILE, stagingArea);
    }

    public static void log() {
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        logHelper(headCommit, sha1(serialize(headCommit)));
    }

    private static void logHelper(Commit currentCommit, String commitID) {
        String parentCommitID = currentCommit.getParentID();
        logPrintHelper(currentCommit, commitID);
        if (parentCommitID == null) {
            return;
        }
        File parentCommit = join(COMMITS_DIR, parentCommitID);
        logHelper(readObject(parentCommit, Commit.class), parentCommitID);
    }

    private static void logPrintHelper(Commit currentCommit, String commitID) {
        System.out.println("===");
        System.out.println("commit " + commitID);
        if (currentCommit.getSecondParentID() != null) {
            String parentID = currentCommit.getParentID().substring(0, 7);
            String secondParentID = currentCommit.getSecondParentID().substring(0, 7);
            System.out.println("Merge: " + parentID + " " + secondParentID);
        }
        System.out.println("Date: " + currentCommit.getTimeStamp());
        System.out.println(currentCommit.getCommitMessage());
        System.out.println();
    }

    public static void globalLog() {
        List<String> commitIDList = plainFilenamesIn(COMMITS_DIR);
        if (commitIDList != null) {
            for (String commitID : commitIDList) {
                Commit currentCommit = readObject(join(COMMITS_DIR, commitID), Commit.class);
                logPrintHelper(currentCommit, commitID);
            }
        }
    }

    public static void find(String commitMessageToFind) {
        List<String> commitIDList = plainFilenamesIn(COMMITS_DIR);
        boolean commitMessageExist = false;
        if (commitIDList != null) {
            for (String commitID : commitIDList) {
                Commit currentCommit = readObject(join(COMMITS_DIR, commitID), Commit.class);
                if (currentCommit.getCommitMessage().equals(commitMessageToFind)) {
                    System.out.println(commitID);
                    commitMessageExist = true;
                }
            }
        }
        if (!commitMessageExist) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void status() {
        System.out.println("=== " + "Branches" + " ===");
        List<String> branchesList = plainFilenamesIn(BRANCHES_DIR);
        if (branchesList != null) {
            for (String branch : branchesList) {
                if (branch.equals(readContentsAsString(HEAD_FILE))) {
                    System.out.println("*" + branch);
                } else {
                    System.out.println(branch);
                }
            }
        }
        System.out.println();
        System.out.println("=== " + "Staged Files" + " ===");
        HashMap<String, String> stagingArea = readObject(INDEX_FILE, HashMap.class);
        Set<String> stagingAreaKeys = stagingArea.keySet();
        for (String key : stagingAreaKeys) {
            if (!stagingArea.get(key).equals(DELETE_MARKER)) {
                System.out.println(key);
            }
        }
        System.out.println();
        System.out.println("=== " + "Removed Files" + " ===");
        for (String key : stagingAreaKeys) {
            if (stagingArea.get(key).equals(DELETE_MARKER)) {
                System.out.println(key);
            }
        }
        System.out.println();
        System.out.println("=== " + "Modifications Not Staged For Commit" + " ===");
        System.out.println();
        System.out.println("=== " + "Untracked Files" + " ===");
        Set<String> untrackedFileList = getUntrackedFile();
        for (String fileName : untrackedFileList) {
            System.out.println(fileName);
        }
        System.out.println();
    }

    public static void checkOut(String[] args) {
        if (args.length == 2) {
            checkOutBranch(args[1]);
        } else if (args.length == 4 && args[2].equals("--")) {
            checkOutSpecialCommit(args[1], args[3]);
        } else if (args.length == 3 && args[1].equals("--")) {
            checkOutHeadCommit(args[2]);
        } else {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    private static void checkOutHeadCommit(String fileName) {
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        if (!headCommit.getNameIDMap().containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String blobID = headCommit.getNameIDMap().get(fileName);
        checkOutFile(fileName, blobID);
    }


    private static void checkOutSpecialCommit(String commitID, String fileName) {
        /* handle the shortened ID search by method getCommitByID */
        if (getCommitByID(commitID) == null) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        Commit specialCommit = getCommitByID(commitID);
        if (!specialCommit.getNameIDMap().containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        String blobID = specialCommit.getNameIDMap().get(fileName);
        checkOutFile(fileName, blobID);
    }

    private static void checkOutBranch(String givenBranchName) {
        File givenBranchFile = join(BRANCHES_DIR, givenBranchName);
        if (!givenBranchFile.exists()) {
            System.out.println("No such branch exists.");
            return;
        } else if (givenBranchName.equals(readContentsAsString(HEAD_FILE))) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        Commit givenHeadCommit = getBranchHeadCommit(givenBranchName);
        Set<String> untrackedFile = getUntrackedFile();
        if (!untrackedFile.isEmpty()
                && overwritten(untrackedFile, givenHeadCommit.getNameIDMap())) {
            System.out.println("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            return;
        }
        checkOutFilesInCommit(givenHeadCommit);
        /* delete the file be tracked in headCommit but not tracked in given headCommit */
        for (String fileName
                : getBranchHeadCommit(readContentsAsString(HEAD_FILE)).getNameIDMap().keySet()) {
            if (!givenHeadCommit.getNameIDMap().containsKey(fileName)) {
                restrictedDelete(join(CWD, fileName));
            }
        }
        /* view the given branch as working branch */
        writeContents(HEAD_FILE, givenBranchName);

        /* clean up the staging area */
        writeObject(INDEX_FILE, new HashMap<>());
    }

    /**
     * check out all files in given Commit into CWD
     */
    private static void checkOutFilesInCommit(Commit givenCommit) {
        for (HashMap.Entry<String, String> entry
                : givenCommit.getNameIDMap().entrySet()) {
            String fileName = entry.getKey();
            String blobID = entry.getValue();
            checkOutFile(fileName, blobID);
        }
    }

    /* check if the untrackedFile will be overWritten*/
    private static boolean overwritten(Set<String> fileSet, TreeMap<String, String> map) {
        for (String fileName : fileSet) {
            if (map.containsKey(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static void checkOutFile(String fileName, String blobID) {
        File fileToCheckOut = join(CWD, fileName);
        Blob checkOutBlob = readObject(join(BLOBS_DIR, blobID), Blob.class);
        writeContents(fileToCheckOut, checkOutBlob.getContent());
    }

    /* get the untracked file list in CWD */
    private static HashSet<String> getUntrackedFile() {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        if (filesInCWD == null) {
            return new HashSet<>();
        }
        HashSet<String> untrackedFileSet = new HashSet<>();
        HashMap<String, String> stagingArea = readObject(INDEX_FILE, HashMap.class);
        Set<String> stagingAreaKeys = stagingArea.keySet();
        Set<String> headCommitMaps = getBranchHeadCommit(readContentsAsString(HEAD_FILE)).
                getNameIDMap().keySet();
        for (String fileName : filesInCWD) {
            if (!headCommitMaps.contains(fileName) && !stagingAreaKeys.contains(fileName)) {
                untrackedFileSet.add(fileName);
            }
        }
        return untrackedFileSet;
    }

    public static void branch(String branchName) {
        File newBranch = join(BRANCHES_DIR, branchName);
        if (newBranch.exists()) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        try {
            newBranch.createNewFile();
        } catch (IOException excp) {
            throw new RuntimeException(excp);
        }
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        writeContents(newBranch, sha1(serialize(headCommit)));
    }

    public static void rmBranch(String rmBranchName) {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
        List<String> branchFileName = plainFilenamesIn(BRANCHES_DIR);
        if (!branchFileName.contains(rmBranchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if (rmBranchName.equals(readContentsAsString(HEAD_FILE))) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        File rmBranchFile = join(BRANCHES_DIR, rmBranchName);
        rmBranchFile.delete();
    }

    public static void reset(String resetCommitID) {
        File resetCommitFile = join(COMMITS_DIR, resetCommitID);
        if (!resetCommitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }
        Commit resetCommit = readObject(resetCommitFile, Commit.class);
        Set<String> untrackedFile = getUntrackedFile();
        if (!untrackedFile.isEmpty()
                && overwritten(untrackedFile, resetCommit.getNameIDMap())) {
            System.out.println("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            return;
        }
        checkOutFilesInCommit(resetCommit);
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        for (String fileName : headCommit.getNameIDMap().keySet()) {
            if (!resetCommit.getNameIDMap().containsKey(fileName)) {
                restrictedDelete(join(CWD, fileName));
            }
        }
        writeContents(join(BRANCHES_DIR, readContentsAsString(HEAD_FILE)), resetCommitID);
        writeObject(INDEX_FILE, new HashMap<>());
    }

    public static void merge(String givenBranchName) {
        HashMap<String, String> stagingArea = readObject(INDEX_FILE, HashMap.class);
        if (!stagingArea.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        File branchFile = join(BRANCHES_DIR, givenBranchName);
        if (!branchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (givenBranchName.equals(readContentsAsString(HEAD_FILE))) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        Commit givenBranchHeadCommit = getBranchHeadCommit(givenBranchName);
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        Set<String> untrackedFile = getUntrackedFile();
        if (!untrackedFile.isEmpty()
                && overwritten(untrackedFile, givenBranchHeadCommit.getNameIDMap())) {
            System.out.println("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            return;
        }
        String givenBranchHeadCommitID = readContentsAsString(join(BRANCHES_DIR, givenBranchName));
        String activeBranch = readContentsAsString(HEAD_FILE);
        String headCommitID = readContentsAsString(join(BRANCHES_DIR, activeBranch));
        String splitPointID = getSplitPointID(givenBranchHeadCommitID, headCommitID);
        Commit splitPoint = getCommitByID(splitPointID);
        if (splitPointID.equals(givenBranchHeadCommitID)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        if (splitPointID.equals(headCommitID)) {
            checkOutBranch(givenBranchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        TreeMap<String, String> splitPointMap = splitPoint.getNameIDMap();
        TreeMap<String, String> headCommitMap = headCommit.getNameIDMap();
        TreeMap<String, String> givenHeadCommitMap = givenBranchHeadCommit.getNameIDMap();
        Set<String> allFiles = new HashSet<>(splitPointMap.keySet());
        allFiles.addAll(headCommitMap.keySet());
        allFiles.addAll(givenHeadCommitMap.keySet());
        Set<String> conflictFiles = new HashSet<>();
        for (String fileName : allFiles) {
            String sID = splitPointMap.get(fileName);
            String hID = headCommitMap.get(fileName);
            String gID = givenHeadCommitMap.get(fileName);
            boolean sIDExist = (sID != null);
            boolean hIDExist = (hID != null);
            boolean gIDExist = (gID != null);
            if ((sIDExist && hIDExist && gIDExist && sID.equals(hID) && !sID.equals(gID))
                    || !sIDExist && gIDExist && !hIDExist) {
                mergeCheck(fileName, gID, stagingArea);
            } else if (sIDExist && hIDExist && !gIDExist && Objects.equals(sID, hID)) {
                mergeDelete(fileName, stagingArea);
            } else if (sIDExist && hIDExist && gIDExist
                    && !sID.equals(hID) && sID.equals(gID)) {
                continue;
            } else if ((!sIDExist && !gIDExist && hIDExist) || Objects.equals(hID, gID)
                    || (sIDExist && !hIDExist && Objects.equals(sID, gID))) {
                continue;
            } else {
                conflictFiles.add(fileName);
            }
        }
        writeObject(INDEX_FILE, stagingArea);
        for (String fileName : conflictFiles) {
            conflictProcess(fileName, givenBranchName);
            add(fileName);
        }
        String mergeMessage = String.format("Merged %s into %s.",
                givenBranchName, readContentsAsString(HEAD_FILE));
        commit(mergeMessage, givenBranchHeadCommitID);
        if (!conflictFiles.isEmpty()) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private static void mergeDelete(String fileName, HashMap<String, String> stagingArea) {
        File deleteFile = join(CWD, fileName);
        if (deleteFile.exists()) {
            deleteFile.delete();
        }
        stagingArea.put(fileName, DELETE_MARKER);
        writeObject(INDEX_FILE, stagingArea);
    }

    private static void mergeCheck(String fileName, String gID, HashMap<String, String> map) {
        checkOutFile(fileName, gID);
        map.put(fileName, gID);
    }

    private static void conflictProcess(String fileName, String givenBranchName) {
        File conflictFile = join(CWD, fileName);
        Commit headCommit = getBranchHeadCommit(readContentsAsString(HEAD_FILE));
        String headCommitBlobID = headCommit.getNameIDMap().get(fileName);
        Commit givenBranchHeadCommit = getBranchHeadCommit(givenBranchName);
        String givenCommitBlobID = givenBranchHeadCommit.getNameIDMap().get(fileName);
        String headBlobContent = "";
        if (headCommitBlobID != null) {
            Blob headBlob = readObject(join(BLOBS_DIR, headCommitBlobID), Blob.class);
            headBlobContent = new String(headBlob.getContent(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        String givenBlobContent = "";
        if (givenCommitBlobID != null) {
            Blob givenBlob = readObject(join(BLOBS_DIR, givenCommitBlobID), Blob.class);
            givenBlobContent = new String(givenBlob.getContent(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        writeContents(conflictFile,
                "<<<<<<< HEAD\n",
                headBlobContent,
                "=======\n",
                givenBlobContent,
                ">>>>>>>\n");
    }

    private static String getSplitPointID(String givenBranchHeadCommitID, String headCommitID) {
        Set<String> allParentCommitID = new HashSet<>(getAllParent(headCommitID));
        allParentCommitID.retainAll(getAllParent(givenBranchHeadCommitID));
        return getCommonAncestorID(allParentCommitID);
    }
    private static String getCommonAncestorID(Set<String> allParentCommitID) {
        for (String splitPointID: allParentCommitID) {
            boolean isOtherParent = false;
            for (String parentCommitID: allParentCommitID) {
                if (parentCommitID.equals(splitPointID)) {
                    continue;
                }
                Set<String> strictAncestor = getAllParent(parentCommitID);
                strictAncestor.remove(parentCommitID);
                if (strictAncestor.contains(splitPointID)) {
                    isOtherParent = true;
                    break;
                }
            }
            if (!isOtherParent) {
                return splitPointID;
            }
        }
        return null;
    }
    private static Set<String> getAllParent(String givenHeadCommitID) {
        Set<String> visited = new HashSet<>();
        dfsGetAllParent(givenHeadCommitID, visited);
        return visited;
    }
    private static void dfsGetAllParent(String givenCommitID, Set<String> visited) {
        if (givenCommitID == null || visited.contains(givenCommitID)) {
            return;
        }
        visited.add(givenCommitID);
        Commit givenCommit = getCommitByID(givenCommitID);
        if (givenCommit == null) {
            System.exit(0);
        }
        dfsGetAllParent(givenCommit.getParentID(), visited);
        if (givenCommit.getSecondParentID() != null) {
            dfsGetAllParent(givenCommit.getSecondParentID(), visited);
        }
    }
}
