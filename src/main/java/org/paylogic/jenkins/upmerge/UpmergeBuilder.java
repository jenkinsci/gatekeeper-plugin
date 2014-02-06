package org.paylogic.jenkins.upmerge;

import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.jenkins.LogMessageSearcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchImpl;

import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;

/**
 * UpmergeBuilder!
 */
@Log
public class UpmergeBuilder extends Builder {

    @DataBoundConstructor
    public UpmergeBuilder() {
    }

    /**
     * Here we should do upmerging.
     *
     * So:
     * - Fetch case info using CASE_ID parameter (should be given).
     * - NOT !! Create 'ReleaseBranch' object from case info and a nextBranch object which is releasebranch.copy().next();
     * - Create ReleaseBranch object from current branch, we may expect that the GatekeeperPlugin set the correct one.
     * - Initiate UpMerge sequence....
     *   - Try to pull new code from nextBranch.getNext();
     *   - Try to merge this new code with releaseBranch();
     *   - Commit this shiny new code.
     *   - Set a flag somewhere, indicating that this upmerge has been done.
     *   - Repeat UpMerge sequence for next releases until there are no moar releases.
     * - In some post-build thingy, push these new branches if all went well.
     * - We SHOULD not have to do any cleanup actions, because workspace is updated every build.
     * - Rely on the FogbugzPlugin (dependency, see pom.xml) to do reporting of our upmerges.
     * - Trigger new builds on all branches that have been merged.
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("--------------------- Now Upmerging ----------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeepring.", e);
            l.append("Exception occured, build aborting...\n");
            LogMessageSearcher.logMessage(listener, e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        PrintStream l = listener.getLogger();
        EnvVars envVars = build.getEnvironment(listener);
        String featureBranch = envVars.get("FEATURE_BRANCH", "");
        String targetBranch = envVars.get("TARGET_BRANCH", "");
        String commitUsername = envVars.get("COMMIT_USER_NAME", "");
        int usableCaseId = 0;
        String givenCaseId = envVars.get("CASE_ID", "");
        if (givenCaseId != "") {
            usableCaseId = Integer.parseInt(givenCaseId);
        }

        /* Get branch name using MercurialBackend, which we'll need later on as well. */
        AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);

        /* Get a ReleaseBranch compatible object to bump release branch versions with. */
        /* TODO: resolve user ReleaseBranchImpl of choice here, learn Java Generics first ;) */
        ReleaseBranch releaseBranch = new ReleaseBranchImpl(targetBranch);

        /*
         Do actual upmerging in this loop, until we can't upmerge no more.
         Will not attempt to Upmerge to branches that were not in the 'hg branches' output.
        */

        // Pull to also get new releases created during tests.
        amm.pull();
        amm.update("");
        amm.merge();
        amm.commit("[Jenkins Upmerging] Merged heads on " + releaseBranch.getName(), commitUsername);

        List<String> branchList = amm.getBranchNames(false);
        String latestBranchToPush, releaseBranchName;
        latestBranchToPush = releaseBranchName = releaseBranch.getName();
        ReleaseBranch nextBranch = releaseBranch.copy();
        nextBranch.next(branchList);
        String nextBranchName = nextBranch.getName();
        while(nextBranchName != releaseBranchName) {
            amm.update(nextBranchName);
            amm.mergeWorkspaceWith(releaseBranchName);
            amm.commit(
                    "[Jenkins Upmerging] Merged " + nextBranchName + " with " + releaseBranchName,
                    commitUsername);

            LogMessageSearcher.logMessage(
                    listener, "Upmerged " + releaseBranchName + "' to '" + nextBranchName + "'.");

            latestBranchToPush = nextBranchName;

            // Bump releases
            releaseBranch.next(branchList);
            releaseBranchName = releaseBranch.getName();
            nextBranch.next(branchList);
            nextBranchName = nextBranch.getName();
        }
        if (amm.getBranchNames(true).contains(featureBranch))
            amm.push(featureBranch, latestBranchToPush);
        else
            amm.push(latestBranchToPush);
        LogMessageSearcher.logMessage(listener, "Pushed changes to repository, on branch '" + latestBranchToPush + "'.");

        return true;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link UpmergeBuilder}. Used as a singleton. Stores global UpmergePlugin settings.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() throws Exception {
            super();
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Perform Upmerging of release branches.";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }
    }
}
