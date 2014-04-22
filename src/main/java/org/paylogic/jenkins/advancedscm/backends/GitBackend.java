package org.paylogic.jenkins.advancedscm.backends;


import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import org.apache.tools.ant.taskdefs.email.EmailAddress;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.Branch;
import org.paylogic.jenkins.advancedscm.backends.helpers.AdvancedCliGit;
import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchImpl;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchInvalidException;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class GitBackend extends BaseBackend implements AdvancedSCMManager {

    private final AbstractBuild build;
    private final Launcher launcher;
    private final BuildListener listener;
    private final GitSCM scm;
    private final GitClient git;
    private final AdvancedCliGit rawGit;

    public GitBackend(AbstractBuild build, Launcher launcher, BuildListener listener, GitSCM scm) throws Exception {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
        this.scm = scm;
        this.git = scm.createClient(listener, build.getEnvironment(listener), build);
        this.rawGit = new AdvancedCliGit(scm, new File(build.getWorkspace().absolutize().getRemote()), listener, build.getEnvironment(listener));
    }

    /**
     * Get branches from command line output,
     * and put them in a List with Branches so it's nice to work with.
     * @param all : get all or only open branches
     *
     * @return List of Branches
     */
    public List<Branch> getBranches(boolean all) throws AdvancedSCMException {
        List<Branch> result = new ArrayList<Branch>();
        try {
            for (hudson.plugins.git.Branch branch : git.getRemoteBranches()) {
                String [] branchNameParts = branch.getName().split("/");
                result.add(new Branch(branchNameParts[branchNameParts.length - 1], null, branch.getSHA1String()));
            }
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }

        return result;
    }

    /**
     * Get local branches from command line output,
     * and put them in a List with Branches so it's nice to work with.
     *
     * @return List of Branches
     */
    public List<Branch> getLocalBranches() throws AdvancedSCMException {
        List<Branch> result = new ArrayList<Branch>();
        try {
            for (hudson.plugins.git.Branch branch : git.getBranches()) {
                if (!branch.getName().contains("/"))
                    result.add(new Branch(branch.getName(), null, branch.getSHA1String()));
            }
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }

        return result;
    }


    /**
     * Get branches from command line output,
     * and put them in a List so it's nice to work with.
     * @return List of String
     */
    public List<String> getLocalBranchNames() throws AdvancedSCMException {
        List<String> list = new ArrayList<String>();
        for (Branch branch: this.getLocalBranches()) {
            list.add(branch.getBranchName());
        }
        return list;
    }


    /**
     * Get the current branch name in the workspace.
     *
     * @return String with branch name in it.
     */
    public String getBranch() {
       return null;
       // TODO: not clear how to get current branch with git client
    }

    /**
     * Updates workspace to given revision/branch.
     *
     * @param revision : String with revision, hash or branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException {
        if (getBranchNames(false).contains(revision)) {
            try {
                git.checkout("HEAD", revision);
            }
            catch (Exception exception) {

            }
        }
        try {
            git.checkout().ref(revision).execute();
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Updates workspace to given revision/branch with cleaning.
     *
     * @param revision : String with revision, hash of branchname to update to.
     */
    public void updateClean(String revision) throws AdvancedSCMException {
        update(revision);
        clean();
    }

    /**
     * Strip out local commits which are not pushed yet.
     */
    public void stripLocal() throws AdvancedSCMException {
        clean();
    }

    /**
     * Cleans workspace from artifacts.
     */
    public void clean() throws AdvancedSCMException {
        try {
            git.clean();
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Merge current workspace with given revision.
     * Do not forget to commit merge afterwards manually.
     *
     * @param revision : String with revision, hash or branchname to merge with.
     * @param updateTo : String with revision, hash or branchname to update working copy to before actual merge.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String mergeWorkspaceWith(String revision, String updateTo, String message, String username) throws AdvancedSCMException {
        try {
            if (updateTo != null)
                update(updateTo);
            ObjectId rev;
            try {
                rev = git.revParse("feature/" + revision);
            }
            catch (GitException exception) {
                try {
                    rev = git.revParse("origin/" + revision);
                }
                catch (GitException exc) {
                    rev = git.revParse(revision);
                }
            }
            EmailAddress address = new EmailAddress(username);
            git.setAuthor(address.getName(), address.getName());
            git.setCommitter(address.getName(), address.getName());
            git.merge().setRevisionToMerge(rev).execute();
            return null;
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Merge possible current branch's heads.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     * @return String : Output of merge command (should be empty if all went well)
     */
    public String merge(String message, String username) throws AdvancedSCMException {
        return null;
    }

    /**
     * Executes 'push' command for given branches
     */
    public String push(String... branchNames) throws AdvancedSCMException {
        List<String> repoBranchNames = getLocalBranchNames();
        try {
            for (String branch: branchNames) {
                if (repoBranchNames.contains(branch))
                    git.push().to(new URIish(git.getRemoteUrl("origin"))).ref(branch).execute();
            }
            return null;
        }
        catch (URISyntaxException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Executes 'pull' command
     *
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull() throws AdvancedSCMException {
        return pull(null, "master");
    }

    /**
     * Pulls changes from remotes. Give it a remote to pull changes from there.
     *
     * @param remote
     * @throws org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException
     */
    public String pull(String remote) throws AdvancedSCMException {
        return pull(remote, "master");
    }

    /**
     * Close given branch. Execute hg commit --close-branch -m"message".
     * @param branch: String branch name.
     * @param message : String with message to give this commit.
     * @param username : String commit user name (with email)
     */
    public void closeBranch(String branch, String message, String username) {
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
        try {
            if (remote == null || remote.isEmpty())
                remote = git.getRemoteUrl("origin");
            try {
                rawGit.launchCommand("remote", "remove", "feature");
            }
            catch (GitException exception) {
            }
            rawGit.launchCommand("remote", "add", "-f", "feature", remote);
            rawGit.launchCommand("fetch", "feature", branch);
            return null;
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    public ReleaseBranch getReleaseBranch(String branch) throws ReleaseBranchInvalidException {
        return new ReleaseBranchImpl(branch, "master");
    }
}
