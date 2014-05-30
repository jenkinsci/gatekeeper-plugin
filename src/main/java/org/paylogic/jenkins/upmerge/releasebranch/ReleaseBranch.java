package org.paylogic.jenkins.upmerge.releasebranch;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * Abstract class to represent a Release Branch.
 * Implement one for your use case by filling the ExtensionPoint.
 */
public abstract class ReleaseBranch implements ExtensionPoint {
    private final String startBranch;

    /**
     * Constructor of ReleaseBranch.
     * Split the name of the branch if needed and store locally.
     * @param startBranch Name of branch to start with (String).
     */
    public ReleaseBranch(String startBranch) throws ReleaseBranchInvalidException {
        this.startBranch = startBranch;
    }

    /**
     * Sets the object to the next release.
     * Does not return representation of release.
     */
    public abstract void next();

    /**
     * Sets the object to the next release with check for existing release branches.
     * Does not return representation of release.
     */
    public abstract void next(List<String> branches);

    /**
     * Returns the current branch name as String
     * Output need to be able to be consumed by constructor of ReleaseBranch.
     * @return current branch name
     */
    public abstract String getName();

    /**
     * Returns the current release as String
     * @return current release name
     */
    public abstract String getReleaseName() throws ReleaseBranchInvalidException;

    /**
     * Create a new ReleaseBranch object with the current release branch.
     * @return new ReleaseBranch
     */
    public abstract ReleaseBranch copy() throws ReleaseBranchInvalidException;


    /**
     * Helper method for Jenkins custom extension point.
     * Not in use, as I can't grasp Java generics...
     */
    public static ExtensionList<ReleaseBranch> all() {
        return Jenkins.getInstance().getExtensionList(ReleaseBranch.class);
    }
}

