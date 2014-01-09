package org.paylogic.jenkins.advancedmercurial;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import lombok.extern.java.Log;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.paylogic.jenkins.advancedmercurial.exceptions.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Custom class for handling branches and merges with Mercurial repositories in Jenkins.
 * Maybe one day we should try to merge this back to the Jenkins Mercurial plugin, as this is not really a plugin.
 */
@Log
public class AdvancedMercurialManager {

    private String hgExe;
    private AdvancedHgExe advancedHgExe;
    private MercurialSCM wantedSCM;
    private AbstractBuild build;
    private PrintStream l;
    private static final String REPO_SUBDIR_MACRO = "REPO_SUBDIR";

    public AdvancedMercurialManager(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        this.hgExe = new MercurialSCM.DescriptorImpl().getHgExe();
        this.build = build;
        this.l = listener.getLogger();

        String givenRepoSubdir = build.getEnvironment(listener).get(this.REPO_SUBDIR_MACRO, "");

        SCM scm = build.getProject().getScm();
        if (scm instanceof MercurialSCM) {
            this.wantedSCM = (MercurialSCM) scm;
        } else if (scm instanceof MultiSCM) {
            java.util.List<hudson.scm.SCM> scms = ((MultiSCM) scm).getConfiguredSCMs();
            if (scms.size() > 1) {
                // loop and find correct repo to apply credentials on
                for (SCM s: ((MultiSCM) scm).getConfiguredSCMs()) {
                    // Only if typeof scm is mercurial
                    if (s instanceof MercurialSCM) {
                        // AND we find subdir as subdir
                        String subDir = ((MercurialSCM) s).getSubdir();
                        if (subDir != null) {
                            if (subDir.equals(givenRepoSubdir)) {
                                this.wantedSCM = ((MercurialSCM) s);
                            }
                        }
                    }
                }
            }
            else {
                this.wantedSCM = ((MercurialSCM) scms.get(0));
            }
        }
        if (this.wantedSCM == null) {
            throw new Exception("AdvancedMercurialManager does not work with repositories other than MercurialSCM or MercurialSCM in MultiSCM. But you can make that happen!");
        }
        // Awesome HgExe object which is awesome ;)
        this.advancedHgExe = new AdvancedHgExe(this.wantedSCM, launcher, build, listener,
                this.build.getWorkspace().child(givenRepoSubdir));
    }

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List  with MercurialBranches so it's nice to work with.
     * @return List of MercurialBranches
     */
    public List<MercurialBranch> getBranches() {
        String rawBranches = "";
        try {
            rawBranches = this.advancedHgExe.branches();
        } catch (Exception e) {
            l.append(e.toString());
            return null;
        }
        List<MercurialBranch> list = new ArrayList<MercurialBranch>();
        for (String line: rawBranches.split("\n")) {
            // line should contain: <branchName>                 <revision>:<hash>  (yes, with lots of whitespace)
            String[] seperatedByWhitespace = line.split("\\s+");
            String branchName = seperatedByWhitespace[0];
            String[] seperatedByColon = seperatedByWhitespace[1].split(":");
            int revision = Integer.parseInt(seperatedByColon[0]);
            String hash = seperatedByColon[1];

            list.add(new MercurialBranch(branchName, revision, hash));
        }
        return list;
    }

    /**
     * Get Mercurial branches from command line output,
     * and put them in a List so it's nice to work with.
     * @return List of String
     */
    public List<String> getBranchNames() {
        List<String> list = new ArrayList<String>();
        for (MercurialBranch branch: this.getBranches()) {
            list.add(branch.getBranchName());
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
    public void update(String revision) throws Exception{
        String output = "";
        try {
            output = this.advancedHgExe.update(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during update of workspace.", e);
            l.append(e.toString());
            throw e;
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }
    }

    /**
     * Updates workspace to given revision/branch with cleaning. Executes hg update -C <revision>.
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws Exception{
        String output = "";
        try {
            output = this.advancedHgExe.updateClean(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during update of workspace.", e);
            l.append(e.toString());
            throw e;
        }
        if (output.contains("abort: unknown revision")) {
            throw new UnknownRevisionException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }
    }

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws Exception {
        String[] out = this.advancedHgExe.out();
        if (out.length > 0) {
            String output = "";
            try {
                output = this.advancedHgExe.strip(out);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Exception occured during strip.", e);
                l.append(e.toString());
                throw e;
            }
            if (output.contains("abort:")) {
                throw new MercurialException(output);
            }
        }
    }

    /**
     * Cleans workspace from artifact. Executes hg purge.
     */
    public void clean() throws Exception{
        String output = "";
        try {
            output = this.advancedHgExe.clean();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during cleaning of workspace.", e);
            l.append(e.toString());
            throw e;
        }
        if (output.contains("abort:")) {
            throw new MercurialException(output);
        }
    }


    /**
     * Commit the workspace changes with the given message. Executes hg commit -m <message>.
     * @param message : String with message to give this commit.
     */
    public void commit(String message, String username) throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.commit(message, username);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured while trying to commit workspace changes.");
            l.append(e.toString());
            throw e;
        }

        if (output.contains("abort:")) {
            throw new MercurialException(output);
        }
    }

    /**
     * Merge current workspace with given revision. Executes hg merge <revision>.
     * Do not forget to commit merge afterwards manually.
     * @param revision : String with revision, hash or branchname to merge with.
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String mergeWorkspaceWith(String revision) throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.merge(revision);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during merge of workspace with " + revision + ".", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                log.log(Level.INFO, "Throwing MercurialMergeConflictException.");
                throw new MercurialMergeConflictException(output);
            } else {
                throw e;
            }
        }

        if (output.contains("abort: merging") && output.contains("has no effect")) {
            throw new MergeWontHaveEffectException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }

        return output;
    }

    /**
     * Merge possible current branch's heads.
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String merge() throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.merge("");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception occured during merge of the heads.", e);
            l.append(e.toString());

            if (output.contains("conflicts during merge") || e.toString().contains("conflicts during merge")) {
                log.log(Level.INFO, "Throwing MercurialMergeConflictException.");
                throw new MercurialMergeConflictException(output);
            } else {
                throw e;
            }
        }

        if (output.contains("abort: merging") && output.contains("has no effect")) {
            throw new MergeWontHaveEffectException(output);
        }

        return output;
    }


    /**
     * Update workspace to 'updateTo' and then merge that workspace with 'revision'.
     * Executes hg update <updateTo> && hg merge <revision>.
     * Do not forget to commit merge afterwards manually.
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update to before merge.
     * @return String : output of command run.
     */
    public String mergeWorkspaceWith(String revision, String updateTo) throws Exception {
        this.update(updateTo);
        return this.mergeWorkspaceWith(revision);
    }


    /**
     * Executes 'hg push'
     */
    public String push() throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.push();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Execption during push :(", e);
            l.append(e.toString());
            throw e;
        }

        if (output.contains("abort: push creates new remote head")) {
            throw new PushCreatesNewRemoteHeadException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }

        return output;
    }

    /**
     * Executes 'hg pull'
     * @throws MercurialException
     */
    public String pull() throws Exception {
        return this.pull("");
    }

    /**
     * Executes 'hg pull <remote>'. Give it a remote to pull changes from there.
     * @throws MercurialException
     */
    public String pull(String remote) throws Exception {
        return this.pull(remote, "");
    }

    /**
     * Executes 'hg pull <remote> -b <branch>'. Give it a remote to pull changes from there.
     * @throws MercurialException
     */
    public String pull(String remote, String branch) throws Exception {
        String output = "";
        try {
            if (remote.isEmpty()) {
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
            throw e;
        }

        if (output.contains("abort:")) {
            throw new MercurialException(output);
        }
        return output;
    }


    public String pushWithNewBranches() throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.push("--new-branch");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Execption during push :(", e);
            l.append(e.toString());
            throw e;
        }

        if (output.contains("abort: push creates new remote head")) {
            throw new PushCreatesNewRemoteHeadException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }

        return output;
    }

    public String pushCertainBranch(String branchname) throws Exception {
        String output = "";
        try {
            output = this.advancedHgExe.push("-b", branchname, "--new-branch");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during push :(", e);
            l.append(e.toString());
            throw e;
        }

        if (output.contains("abort: push creates new remote head")) {
            throw new PushCreatesNewRemoteHeadException(output);
        } else if (output.contains("abort:")) {
            throw new MercurialException(output);
        }

        return output;
    }

    @Deprecated
    public String push(String revision) throws Exception {
        return this.push();
    }

}
