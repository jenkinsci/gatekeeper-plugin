package org.paylogic.jenkins.upmerge.releasebranch;

/**
 * Exception to throw when given branch name does not comply with excpected branch format.
 */
public class ReleaseBranchInvalidException extends Exception {
    public ReleaseBranchInvalidException(String message) {
        super(message);
    }
}
