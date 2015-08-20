package org.paylogic.jenkins.gatekeeper;

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
import org.jenkinsci.plugins.envinject.EnvInjectBuilderContributionAction;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.paylogic.jenkins.LogMessageSearcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;
import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Created by bubenkoff on 20.12.13.
 */
@Log
public class GatekeeperCommit extends Builder {

    public final String commitUsername;

    @DataBoundConstructor
    public GatekeeperCommit(String commitUsername) {
        this.commitUsername = commitUsername;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Gatekeeper commit --------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeeper commit.", e);
            l.append("Exception occured, build aborting...\n");
            LogMessageSearcher.logMessage(listener, e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        EnvVars envVars = build.getEnvironment(listener);
        String featureBranch = envVars.get("FEATURE_BRANCH", "");
        String targetBranch = envVars.get("TARGET_BRANCH", "");

        AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);
        commit(amm, listener, envVars, targetBranch, featureBranch, commitUsername);

        // pass branches to push to later build actions
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("BRANCHES_TO_PUSH", targetBranch);
        build.addAction(new EnvInjectBuilderContributionAction(vars));
        return true;
    }

    private void commit(AdvancedSCMManager amm, BuildListener listener, EnvVars envVars, String targetBranch, String featureBranch, String commitUsername) throws AdvancedSCMException {
        amm.commit("[Jenkins Integration Merge] Merged " + featureBranch + " into "
                        + targetBranch,
                commitUsername);
        if (amm.getBranchNames(false).contains(featureBranch)) {
            // we have to close feature branch
            amm.closeBranch(featureBranch, "[Jenkins Integration Merge] Closing feature branch " + featureBranch, commitUsername);
            amm.update(targetBranch);
        }
        LogMessageSearcher.logMessage(listener, "Gatekeeper merge was committed.");
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
            return "Perform Gatekeeper commit.";
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
