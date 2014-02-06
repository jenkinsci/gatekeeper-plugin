package org.paylogic.jenkins.advancedscm.backends;


import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitSCM;
import hudson.Launcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.Branch;
import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;

import java.util.List;
import java.util.logging.Level;

public class GitBackend implements AdvancedSCMManager {

    private final AbstractBuild build;
    private final Launcher launcher;
    private final BuildListener listener;
    private final GitSCM scm;

    public GitBackend(AbstractBuild build, Launcher launcher, BuildListener listener, GitSCM scm) throws Exception {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
        this.scm = scm;
        // TODO: implement git support
        throw new Exception("Git support is currently not implemented.");
    }
    /**
     * Get Mercurial branches from command line output,
     * and put them in a List with Branches so it's nice to work with.
     * @param all : get all or only open branches
     *
     * @return List of Branches
     */
    public List<Branch> getBranches(boolean all) {
        return null;
    }

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List so it's nice to work with.
     * @param all : get all or only open branches
     *
     * @return List of String
     */
    public List<String> getBranchNames(boolean all) {
        return null;
    }

    /**
     * Get the current branch name in the workspace.
     *
     * @return String with branch name in it.
     */
    public String getBranch() {
        return null;
    }

    /**
     * Updates workspace to given revision/branch.
     *
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException {

    }

    /**
     * Updates workspace to given revision/branch with cleaning.
     *
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws AdvancedSCMException {

    }

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws AdvancedSCMException {

    }

    /**
     * Cleans workspace from artifacts.
     */
    public void clean() throws AdvancedSCMException {

    }

    /**
     * Commit the workspace changes with the given message.
     *
     * @param message  : String with message to give this commit.
     * @param username
     */
    public void commit(String message, String username) throws AdvancedSCMException {

    }

    /**
     * Merge current workspace with given revision.
     * Do not forget to commit merge afterwards manually.
     *
     * @param revision : String with revision, hash or branchname to merge with.
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String mergeWorkspaceWith(String revision) throws AdvancedSCMException {
        return null;
    }

    /**
     * Merge possible current branch's heads.
     *
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String merge() throws AdvancedSCMException {
        return null;
    }

    /**
     * Update workspace to 'updateTo' and then merge that workspace with 'revision'.
     * Do not forget to commit merge afterwards manually.
     *
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update to before merge.
     * @return String : output of command run.
     */
    public String mergeWorkspaceWith(String revision, String updateTo) throws AdvancedSCMException {
        return null;
    }

    /**
     * Executes 'push' command for given branches
     */
    public String push(String... branchNames) throws AdvancedSCMException {
        return null;
    }

    /**
     * Executes 'pull' command
     *
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull() throws AdvancedSCMException {
        return null;
    }

    /**
     * Pulls changes from remotes. Give it a remote to pull changes from there.
     *
     * @param remote
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote) throws AdvancedSCMException {
        return null;
    }

    /**
     * Close given branch. Execute hg commit --close-branch -m"message".
     * @param message : String with message to give this commit.
     */
    public void closeBranch(String message, String username) throws AdvancedSCMException {
        return;
    }

    /**
     * Pulls from given repository url. Give it a remote to pull changes from there.
     *
     * @param remote
     * @param branch
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote, String branch) throws AdvancedSCMException {
        return null;
    }
}
