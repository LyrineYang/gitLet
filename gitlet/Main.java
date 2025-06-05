package gitlet;


/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Lyrine Yang
 *  Thanks to my collaborator [Yuteng Huang](https://github.com/isHarryh) who leads me to be a better programmer.
 *  Thanks to Jiang Zhuo and bzWang who brings me a lot.
 */

public class Main {

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     *
     */
    public static boolean argsCheck(String[] args, int length) {
        if (args.length != length) {
            System.out.println("Incorrect operands.");
            System.exit(0);
            return false;
        }
        return true;
    }
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        if (!firstArg.equals("init") && !Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
        switch (firstArg) {
            case "init":
                argsCheck(args, 1);
                Repository.init();
                break;
            case "add":
                argsCheck(args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                argsCheck(args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                argsCheck(args, 2);
                Repository.remove(args[1]);
                break;
            case "log":
                argsCheck(args, 1);
                Repository.log();
                break;
            case "global-log":
                argsCheck(args, 1);
                Repository.globalLog();
                break;
            case "find":
                argsCheck(args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                argsCheck(args, 1);
                Repository.status();
                break;
            case "checkout":
                Repository.checkOut(args);
                break;
            case "branch":
                argsCheck(args, 2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                argsCheck(args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                argsCheck(args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                argsCheck(args, 2);
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
        }
    }
}
