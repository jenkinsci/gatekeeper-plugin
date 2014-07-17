package org.paylogic.jenkins.advancedscm.backends;


import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import lombok.extern.java.Log;
import org.apache.tools.ant.taskdefs.email.EmailAddress;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
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

/**
 * Mercurial Implementation of AdvancedSCMManager
 */
@Log
public class GitBackend extends BaseBackend {

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
        FilePath path = build.getWorkspace();
        EnvVars environment = build.getEnvironment(listener);
        for (GitSCMExtension ext : scm.getExtensions()) {
            FilePath r = ext.getWorkingDirectory(scm, build.getParent(), path, environment, listener);
            if (r!=null) {
                path = r;
            }
        }
        this.git = scm.createClient(listener, environment, build, path);
        this.rawGit = new AdvancedCliGit(
                scm, launcher, build.getBuiltOn(), new File(path.absolutize().getRemote()), listener,
                build.getEnvironment(listener));
        this.repoPath = rawGit.getWorkTree();
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
                if (!branch.getName().contains("/")) {
                    result.add(new Branch(branch.getName(), null, branch.getSHA1String()));
                }
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
    public String getBranch() throws AdvancedSCMException {
        try {
            return rawGit.launchCommand("rev-parse", "--abbrev-ref", "HEAD").trim();
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Updates workspace to given revision/branch.
     *
     * @param revision : String with revision, hash or branchname to update to.
     */
    public void update(String revision) throws AdvancedSCMException {
        if (!revision.isEmpty() && !getLocalBranchNames().contains(revision)) {
            try {
                rawGit.launchCommand("checkout", "-b", revision, "--track", "origin/" + revision);
            }
            catch (Exception exception) {
                throw new AdvancedSCMException(exception.toString());
            }
        } else {
            try {
                git.checkout().ref(revision).execute();
            } catch (InterruptedException exception) {
                throw new AdvancedSCMException(exception.toString());
            }
        }
    }

    public void updateClean(String revision) throws AdvancedSCMException {
        update(revision);
        clean();
    }

    public void stripLocal() throws AdvancedSCMException {
        clean();
        List<String> repoBranchNames = getLocalBranchNames();
        for (String branch: repoBranchNames) {
            git.checkout().branch(branch);
            clean(branch);
            try {
                rawGit.launchCommand("checkout", "-f");
            }
            catch (InterruptedException exception) {
            }
        }
    }

    public void clean() throws AdvancedSCMException {
        try {
            git.clean();
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    public void clean(String revision) throws AdvancedSCMException {
        update(revision);
        try {
            rawGit.launchCommand("reset", "--hard", "origin/" + revision);
        } catch (GitException exception) {

        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
        clean();
    }

    public void mergeWorkspaceWith(
            String revision, String updateTo) throws AdvancedSCMException {
        try {
            ObjectId rev;
            if (updateTo != null) {
                updateClean(updateTo);
                rev = git.revParse(revision);
            }
            else {
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
            }
            EmailAddress address = new EmailAddress("dummy <dummy@foo.bar>");
            rawGit.setAuthor(address.getName(), address.getName());
            rawGit.setCommitter(address.getName(), address.getName());
            rawGit.launchCommand("merge", "--no-commit", "--no-ff", rev.getName());
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    public void commit(String message, String username) throws AdvancedSCMException {
        try {
            EmailAddress address = new EmailAddress(username);
            git.setAuthor(address.getName(), address.getAddress());
            git.setCommitter(address.getName(), address.getAddress());
            git.commit(message);
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    /**
     * Merge possible current branch's heads. Not actual for git backend.
     * @param message : String commit message
     * @param username : String commit user name (with email)
     */
    public void mergeHeads(String message, String username) throws AdvancedSCMException {
    }

    public void push(String... branchNames) throws AdvancedSCMException {
        List<String> repoBranchNames = getLocalBranchNames();
        try {
            for (String branch: branchNames) {
                if (repoBranchNames.contains(branch)) {
                    git.push().to(new URIish(git.getRemoteUrl("origin"))).ref(branch).execute();
                }
            }
        }
        catch (URISyntaxException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    public void pull() throws AdvancedSCMException {
        pull(null, "master");
    }

    public void pull(String remote) throws AdvancedSCMException {
        pull(remote, "master");
    }

    /**
     * Close given branch. Nothing has to be done in git backend.
     * @param branch: String branch name.
     * @param message : String with message to give this commit.
     * @param username : String commit user name (with email)
     */
    public void closeBranch(String branch, String message, String username) {
        return;
    }

    public void pull(String remote, String branch) throws AdvancedSCMException {
        try {
            if (remote == null || remote.isEmpty()) {
                remote = git.getRemoteUrl("origin");
            }
            try {
                rawGit.launchCommand("remote", "rm", "feature");
            }
            catch (GitException exception) {
                // when remote is new, can fail, but it's intentional
            }
            rawGit.launchCommand("remote", "add", "feature", remote);
            try {
                rawGit.launchCommand("fetch", "feature", branch);
            }
            catch (GitException exception) {
                // can be a new local branch, so can fail, but it's intentional
            }
        }
        catch (InterruptedException exception) {
            throw new AdvancedSCMException(exception.toString());
        }
    }

    public ReleaseBranch getReleaseBranch(String branch) throws ReleaseBranchInvalidException {
        return new ReleaseBranchImpl(branch, "master");
    }

    public ReleaseBranch createReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException, ReleaseBranchInvalidException
    {
        {
            try {
                this.update("master");
                git.checkout("HEAD", branch);
                if (releaseFilePath != null && !releaseFilePath.isEmpty()
                        && releaseFileContent != null && !releaseFileContent.isEmpty()) {
                    this.createFile(releaseFilePath, releaseFileContent);
                    git.add(releaseFilePath);
                    EmailAddress address = new EmailAddress(username);
                    git.setAuthor(address.getName(), address.getName());
                    git.setCommitter(address.getName(), address.getName());
                    git.commit(message);
                }
                return getReleaseBranch(branch);
            } catch (Exception e) {
                throw new AdvancedSCMException(e.getMessage());
            }
        }
    }

}
