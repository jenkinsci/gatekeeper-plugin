package org.paylogic.jenkins.advancedscm.backends;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.mercurial.MercurialSCM;
import lombok.extern.java.Log;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.Branch;
import org.paylogic.jenkins.advancedscm.backends.helpers.AdvancedHgExe;
import org.paylogic.jenkins.advancedscm.exceptions.*;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchImpl;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchInvalidException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Mercurial Implementation of AdvancedSCMManager
 */
@Log
public class MercurialBackend extends BaseBackend implements AdvancedSCMManager {

    private String hgExe;
    private AdvancedHgExe advancedHgExe;
    private AbstractBuild build;
    private PrintStream l;

    /**
     * Please do not instantiate objects of this class yourself, use SCMManagerFactory.
     */
    public MercurialBackend(AbstractBuild build, Launcher launcher, BuildListener listener, MercurialSCM scm) throws IOException, InterruptedException {
        this.build = build;
        this.l = listener.getLogger();
        this.advancedHgExe = new AdvancedHgExe(scm, launcher, build, listener);
    }

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List  with MercurialBranches so it's nice to work with.
     * @param all: get all or only open branches
     * @return List of MercurialBranches, empty if no branches are there.
     */
    public List<Branch> getBranches(boolean all) {
        String rawBranches = "";
        String[] args = new String[] {};
        if (all)
            args = new String[] {"-c"};
        try {
            rawBranches = this.advancedHgExe.branches(args);
        } catch (Exception e) {
            l.append(e.toString());
            return new ArrayList<Branch>();
        }
        List<Branch> list = new ArrayList<Branch>();
        for (String line: rawBranches.split("\n")) {
            // line should contain: <branchName>                 <revision>:<hash>  (yes, with lots of whitespace)
            String[] seperatedByWhitespace = line.split("\\s+");
            String branchName = seperatedByWhitespace[0];
            String[] seperatedByColon = seperatedByWhitespace[1].split(":");
            int revision = Integer.parseInt(seperatedByColon[0]);
            String hash = seperatedByColon[1];

            list.add(new Branch(branchName, revision, hash));
        }
        return list;
    }

    /**
     * Get the current branch name in the workspace. Executes 'hg branch'
     * @return String with branch name in it.
     */
    public String getBranch() {
        String branchName = "";
        try {
            branchName = this.advancedHgExe.branch();
        } catch (Exception e) {
            l.append(e.toString());
        }
        return branchName;
    }

    /**
     * Updates workspace to given revision/branch. Executes hg update <revision>.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.update(revision);
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured during update of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    /**
     * Updates workspace to given revision/branch with cleaning. Executes hg update -C <revision>.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.updateClean(revision);
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured during update of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws AdvancedSCMException {
        try {
            String[] out = this.advancedHgExe.out();
            if (out.length > 0) {
                String output = "";
                try {
                    output = this.advancedHgExe.strip(out);
                } catch (Exception e) {
                    MercurialBackend.log.log(Level.SEVERE, "Exception occured during strip.", e);
                    l.append(e.toString());
                    throw new AdvancedSCMException(e.getMessage());
                }
                if (output.contains("abort:")) {
                    throw new AdvancedSCMException(output);
                }
            }
        } catch (Exception e) {
            throw new AdvancedSCMException(e.getMessage());
        }
    }

    /**
     * Cleans workspace from artifact. Executes hg purge.
     */
    public void clean() throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.clean();
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured during cleaning of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    /**
     * Close given branch. Execute hg commit --close-branch -m"message".
     * @param branch: String branch name.
     * @param message : String with message to give this commit.
     * @param username : String commit user name (with email)
     */
    public void closeBranch(String branch, String message, String username) throws AdvancedSCMException {
        String output = "";
        updateClean(branch);
        try {
            output = this.advancedHgExe.commit(message, username, "--close-branch");
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured while trying to close branch commit.");
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    /**
     * Merge current workspace with given revision. Executes hg merge <revision>.
     * Do not forget to commit merge afterwards manually.
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update working copy to before actual merge.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String mergeWorkspaceWith(String revision, String updateTo, String message, String username) throws AdvancedSCMException {
        if (updateTo != null)
            this.update(updateTo);
        String output = "";
        try {
            output = this.advancedHgExe.merge(revision);
            output += this.advancedHgExe.commit(message, username);
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured during merge of workspace with " + revision + ".", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                MercurialBackend.log.log(Level.INFO, "Throwing MergeConflictException.");
                throw new MergeConflictException(output);
            } else {
                throw new AdvancedSCMException(e.getMessage());
            }
        }

        if (output.contains("abort: merging") && output.contains("has no effect")) {
            throw new MergeWontHaveEffectException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }

        return output;
    }

    /**
     * Merge possible current branch's heads.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String merge(String message, String username) throws AdvancedSCMException {
        String output = "";
        try {
            output = this.advancedHgExe.merge("");
            output += this.advancedHgExe.commit(message, username);
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Exception occured during merge of the heads.", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                MercurialBackend.log.log(Level.INFO, "Throwing MergeConflictException.");
                throw new MergeConflictException(output);
            } else {
                throw new AdvancedSCMException(e.getMessage());
            }
        }

        if (output.contains("abort: merging") && output.contains("has no effect")) {
            throw new MergeWontHaveEffectException(output);
        }

        return output;
    }

    /**
     * Executes 'hg push'
     */
    public String push(String... branchNames) throws AdvancedSCMException {
        String output = "";
        try {
            output = this.advancedHgExe.push(branchNames);
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Execption during push :(", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort: push creates new remote head")) {
            throw new PushCreatesNewRemoteHeadException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }

        return output;
    }

    /**
     * Executes 'hg pull'
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull() throws AdvancedSCMException {
        return this.pull("");
    }

    /**
     * Executes 'hg pull <remote>'. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote) throws AdvancedSCMException {
        return this.pull(remote, "");
    }

    /**
     * Executes 'hg pull <remote> -b <branch>'. Give it a remote to pull changes from there.
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote, String branch) throws AdvancedSCMException {
        String output = "";
        try {
            if (remote == null || remote.isEmpty()) {
                output = this.advancedHgExe.pullChanges();
            }
            else if (branch.isEmpty()) {
                remote = this.advancedHgExe.pullChanges(remote);
            }
            else {
                remote = this.advancedHgExe.pullChanges(remote, branch);
            }
        } catch (Exception e) {
            MercurialBackend.log.log(Level.SEVERE, "Error during Mercurial command exceution");
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
        return output;
    }

    public ReleaseBranch getReleaseBranch(String branch) throws ReleaseBranchInvalidException {
        return new ReleaseBranchImpl(branch, "default");
    }
}
