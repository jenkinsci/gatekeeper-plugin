package org.paylogic.jenkins.gatekeeper;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
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
import org.paylogic.jenkins.advancedscm.exceptions.MergeConflictException;

import java.io.PrintStream;
import java.util.logging.Level;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeeper merge.
 */
@Log
public class GatekeeperMerge extends Builder {

    @DataBoundConstructor
    public GatekeeperMerge() {
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Gatekeeper merge ---------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (MergeConflictException e) {
            log.log(Level.SEVERE, "Exception during Gatekeeeper merge.", e);
            l.append("Exception occured, build aborting...\n");
            LogMessageSearcher.logMessage(listener, "Merge conflict occured when Gatekeeper merging, " +
                    "please check the Jenkins buildlog for conflicting files, resolve them, " +
                    "and reassign this case to Mergekeepers.");
            return false;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeeper merge.", e);
            l.append("Exception occured, build aborting...\n");
            LogMessageSearcher.logMessage(listener, e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        EnvVars envVars = build.getEnvironment(listener);
        String featureBranch = envVars.get("FEATURE_BRANCH", "");
        String targetBranch = envVars.get("TARGET_BRANCH", "");
        String featureRepoUrl = envVars.get("REPO_URL", "");
        String okRevision = envVars.get("APPROVED_REVISION", "");
        int usableCaseId = 0;

        String repo_path = envVars.get("REPO_PATH", "");
        AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);
        amm.stripLocal();

        /* Actual Gatekeepering logic. Seperated to work differently when Rietveld support is active. */
        boolean runNormalMerge = okRevision.isEmpty();
        if (!runNormalMerge) { // Use Rietveld support.

            listener.getLogger().append("Trying to merge with revision '" + okRevision + "'.\n");
            listener.getLogger().append("Which should be in repo '" + featureRepoUrl + "', which we will pull.\n");

            /* Actual gatekeepering commands. Invokes mercurial exe */
            amm.pull(featureRepoUrl, featureBranch);
            amm.updateClean(targetBranch);
            amm.clean();
            amm.mergeWorkspaceWith(okRevision);

            LogMessageSearcher.logMessage(listener, "Gatekeeper merge merged " +
                    okRevision + " from " + featureRepoUrl + " to " + targetBranch + ".");
        } else {
            amm.pull("", featureBranch);
            amm.update(targetBranch);
            amm.mergeWorkspaceWith(featureBranch);
            LogMessageSearcher.logMessage(listener, "Gatekeeper merge merged " +
                    featureBranch + " to " + targetBranch + ".");
        }

        return true;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Perform Gatekeeper merge.";
        }
    }
}

