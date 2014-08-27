package org.paylogic.jenkins.advancedscm.backends;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.mercurial.MercurialSCM;
import lombok.extern.java.Log;
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
public class MercurialBackend extends BaseBackend {

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
        this.repoPath = this.advancedHgExe.getFilePath();
    }

    public List<Branch> getBranches(boolean all) {
        String rawBranches = "";
        String[] args = new String[] {};
        if (all) {
            args = new String[]{"-c"};
        }
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

    public String getBranch() throws AdvancedSCMException {
        String branchName = "";
        try {
            branchName = this.advancedHgExe.branch();
        } catch (Exception e) {
            throw new AdvancedSCMException(e.toString());
        }
        return branchName;
    }

    public void update(String revision) throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.update(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during update of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public void updateClean(String revision) throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.updateClean(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occurred during update of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
        clean();
    }

    public void stripLocal() throws AdvancedSCMException {
        try {
            String[] out = this.advancedHgExe.out();
            if (out.length > 0) {
                String output = "";
                try {
                    output = this.advancedHgExe.strip(out);
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Exception occurred during strip.", e);
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

    public void clean() throws AdvancedSCMException{
        String output = "";
        try {
            output = this.advancedHgExe.clean();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during cleaning of workspace.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public void closeBranch(String branch, String message, String username) throws AdvancedSCMException {
        String output = "";
        update(branch);
        try {
            output = this.advancedHgExe.commit(message, username, "--close-branch");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured while trying to close branch commit.");
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public void mergeWorkspaceWith(String revision, String updateTo) throws AdvancedSCMException {
        if (updateTo != null) {
            this.update(updateTo);
        }
        String output = "";
        try {
            output = this.advancedHgExe.merge(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occurred during merge of workspace with " + revision + ".", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                log.log(Level.INFO, "Throwing MergeConflictException.");
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
    }

    public void mergeHeads(String message, String username) throws AdvancedSCMException {
        String output = "";
        try {
            output = this.advancedHgExe.merge("");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occurred during merge of the heads.", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                log.log(Level.INFO, "Throwing MergeConflictException.");
                throw new MergeConflictException(output);
            } else {
                throw new AdvancedSCMException(e.getMessage());
            }
        }

        commit(message, username);
    }

    public void commit(String message, String username) throws AdvancedSCMException {
        String output = "";
        try {
            output = this.advancedHgExe.commit(message, username);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occurred during commit.", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }
        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public void push(String... branchNames) throws AdvancedSCMException {
        String output = "";
        try {
            output = this.advancedHgExe.push(branchNames);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Execption during push :(", e);
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort: push creates new remote head")) {
            throw new PushCreatesNewRemoteHeadException(output);
        } else if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public void pull() throws AdvancedSCMException {
        this.pull("");
    }

    public void pull(String remote) throws AdvancedSCMException {
        this.pull(remote, "");
    }

    public void pull(String remote, String branch) throws AdvancedSCMException {
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
            log.log(Level.SEVERE, "Error during Mercurial command exceution");
            l.append(e.toString());
            throw new AdvancedSCMException(e.getMessage());
        }

        if (output.contains("abort:")) {
            throw new AdvancedSCMException(output);
        }
    }

    public ReleaseBranch getReleaseBranch(String branch) throws ReleaseBranchInvalidException {
        return new ReleaseBranchImpl(branch, "default");
    }

    public ReleaseBranch createReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException
    {
        try {
            this.update("default");
            this.advancedHgExe.branch(branch);
            if (releaseFilePath != null && !releaseFilePath.isEmpty()
                    && releaseFileContent != null && !releaseFileContent.isEmpty()) {
                this.createFile(releaseFilePath, releaseFileContent);
                this.advancedHgExe.add(releaseFilePath, releaseFileContent);
            }
            this.advancedHgExe.commit(message, username);
            return getReleaseBranch(branch);
        } catch (Exception e) {
            throw new AdvancedSCMException(e.getMessage());
        }
    }
}
