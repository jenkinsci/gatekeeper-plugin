package org.paylogic.jenkins.upmerge;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.envinject.EnvInjectBuilderContributionAction;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.jenkins.LogMessageSearcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranch;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * UpmergeBuilder!
 */
@Log
public class UpmergeBuilder extends Builder {

    public final String commitUsername;

    @DataBoundConstructor
    public UpmergeBuilder(String commitUsername) {
        this.commitUsername = commitUsername;
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
        int usableCaseId = 0;
        String givenCaseId = envVars.get("CASE_ID", "");
        if (givenCaseId != "") {
            usableCaseId = Integer.parseInt(givenCaseId);
        }
        /* Get branch name using AdvancedSCMManager, which we'll need later on as well. */
        AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);

        /* Get a ReleaseBranch compatible object to bump release branch versions with. */
        ReleaseBranch releaseBranch = amm.getReleaseBranch(targetBranch);
        String releaseBranchName = releaseBranch.getName();
        /*
         Do actual upmerging in this loop, until we can't upmerge no more.
         Will not attempt to Upmerge to branches that were not in the repo branch list.
        */

        // Pull to also get new releases created during tests.
        amm.pull(null, targetBranch);
        amm.update("");
        amm.merge("[Jenkins Upmerging] Merged heads on " + releaseBranchName, commitUsername);

        List<String> branchList = amm.getBranchNames(true);
        List<String> branchesToPush = new ArrayList<String>();
        branchesToPush.add(targetBranch);
        if (branchList.contains(featureBranch)) {
            // can be not a branch, but a bookmark
            branchesToPush.add(featureBranch);
        }
        ReleaseBranch nextBranch = releaseBranch.copy();
        nextBranch.next(branchList);
        String nextBranchName = nextBranch.getName();
        while(nextBranchName != releaseBranchName) {
            amm.mergeWorkspaceWith(releaseBranchName, nextBranchName,
                    "[Jenkins Upmerging] Merged " + releaseBranchName + " into " + nextBranchName,
                    commitUsername);
            amm.merge("[Jenkins Upmerging] Merged heads on " + nextBranchName, commitUsername);
            LogMessageSearcher.logMessage(
                    listener, "Upmerged " + releaseBranchName + " into " + nextBranchName + ".");
            branchesToPush.add(nextBranchName);
            // Bump releases
            releaseBranch.next(branchList);
            releaseBranchName = releaseBranch.getName();
            nextBranch.next(branchList);
            nextBranchName = nextBranch.getName();
        }

        // pass branches to push to later build actions
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("BRANCHES_TO_PUSH", StringUtils.join(branchesToPush, ","));
        build.addAction(new EnvInjectBuilderContributionAction(vars));
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

        public FormValidation doCheckCommitUsername(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            }
            else {
                return FormValidation.error("Required field");
            }
        }
    }
}
