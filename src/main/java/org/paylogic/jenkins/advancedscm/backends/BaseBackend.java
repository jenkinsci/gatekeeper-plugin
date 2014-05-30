package org.paylogic.jenkins.advancedscm.backends;

import hudson.FilePath;
import lombok.extern.java.Log;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.Branch;
import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchInvalidException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for implementations of AdvancedSCMManager
 */
abstract class BaseBackend  implements AdvancedSCMManager {

    /**
     * Path to the repository folder
     */
    protected FilePath repoPath;

    public List<String> getBranchNames(boolean all) throws AdvancedSCMException {
        List<String> list = new ArrayList<String>();
        for (Branch branch: this.getBranches(all)) {
            list.add(branch.getBranchName());
        }
        return list;
    }

    abstract public List<Branch> getBranches(boolean all) throws AdvancedSCMException;

    abstract public ReleaseBranch createReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException, ReleaseBranchInvalidException;

    public void ensureReleaseBranch(
            String branch, String releaseFilePath, String releaseFileContent, String message, String username)
            throws AdvancedSCMException, ReleaseBranchInvalidException {
        List<String> branchNames = getBranchNames(false);
        if (!branchNames.contains(branch)) {
            createReleaseBranch(branch, releaseFilePath, releaseFileContent, message, username);
        }
    }

    /**
     * Create file in the repository folder
     * @param filename: String relative path to the file from the repository root
     * @param content: String file content (UTF-8 encoding is hard-coded)
     */
    public void createFile(String filename, String content) throws IOException, InterruptedException {
        FilePath file= repoPath.child(filename);
        if (!file.exists()) {
            file.getParent().mkdirs();
        }
        file.write(content, "UTF-8");
    }
}
