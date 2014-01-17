package org.paylogic.jenkins.advancedscm;

import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;

import java.util.List;

public interface AdvancedSCMManager {

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List with Branches so it's nice to work with.
     * @return List of Branches
     */
    public List<Branch> getBranches();

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List so it's nice to work with.
     * @return List of String
     */
    public List<String> getBranchNames();

    /**
     * Get the current branch name in the workspace.
     * @return String with branch name in it.
     */
    public String getBranch();

    /**
     * Updates workspace to given revision/branch.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException;

    /**
     * Updates workspace to given revision/branch with cleaning.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws AdvancedSCMException;

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws AdvancedSCMException;

    /**
     * Cleans workspace from artifacts.
     */
    public void clean() throws AdvancedSCMException;

    /**
     * Commit the workspace changes with the given message.
     * @param message : String with message to give this commit.
     */
    public void commit(String message, String username) throws AdvancedSCMException;

    /**
     * Merge current workspace with given revision.
     * Do not forget to commit merge afterwards manually.
     * @param revision : String with revision, hash or branchname to merge with.
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String mergeWorkspaceWith(String revision) throws AdvancedSCMException;

    /**
     * Merge possible current branch's heads.
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String merge() throws AdvancedSCMException;


    /**
     * Update workspace to 'updateTo' and then merge that workspace with 'revision'.
     * Do not forget to commit merge afterwards manually.
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update to before merge.
     * @return String : output of command run.
     */
    public String mergeWorkspaceWith(String revision, String updateTo) throws AdvancedSCMException;


    /**
     * Executes 'push' command
     */
    public String push() throws AdvancedSCMException;

    /**
     * Executes 'pull' command
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull() throws AdvancedSCMException;

    /**
     * Pulls changes from remotes. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote) throws AdvancedSCMException;

    /**
     * Pulls from given repository url. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote, String branch) throws AdvancedSCMException;

    public String pushWithNewBranches() throws AdvancedSCMException;

    public String pushCertainBranch(String branchname) throws AdvancedSCMException;

}
