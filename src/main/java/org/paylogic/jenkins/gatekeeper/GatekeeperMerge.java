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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.paylogic.jenkins.LogMessageSearcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;
import org.paylogic.jenkins.advancedscm.exceptions.AdvancedSCMException;
import org.paylogic.jenkins.advancedscm.exceptions.MergeConflictException;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchImpl;
import org.paylogic.jenkins.upmerge.releasebranch.ReleaseBranchInvalidException;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.Context;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeeper merge.
 */
@Log
public class GatekeeperMerge extends Builder {

    public final String commitUsername;
    public final String releaseFilePath;
    public final String releaseFileContentTemplate;

    @DataBoundConstructor
    public GatekeeperMerge(String commitUsername, String releaseFilePath, String releaseFileContentTemplate) {
        this.commitUsername = commitUsername;
        this.releaseFilePath = releaseFilePath;
        this.releaseFileContentTemplate = releaseFileContentTemplate;
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

        listener.getLogger().append("Ensuring target release branch " + targetBranch + ".\n");
        ensureReleaseBranch(amm, targetBranch);

        /* Actual Gatekeepering logic. Seperated to work differently when Rietveld support is active. */
        boolean runNormalMerge = okRevision.isEmpty();
        if (!runNormalMerge) { // Use Rietveld support.

            listener.getLogger().append("Trying to merge with revision " + okRevision + ".\n");
            listener.getLogger().append("Which should be in repo " + featureRepoUrl + ", which we will pull.\n");

            /* Actual gatekeepering commands.*/
            amm.pull(featureRepoUrl, featureBranch);
            amm.updateClean(targetBranch);
            amm.mergeWorkspaceWith(okRevision, null, "[Jenkins Integration Merge] Merged " + featureBranch + " into "
                    + targetBranch,
                    commitUsername);
            LogMessageSearcher.logMessage(listener, "Gatekeeper merge merged " +
                    okRevision + " from " + featureRepoUrl + " to " + targetBranch + ".");
        } else {
            amm.pull(featureRepoUrl, featureBranch);
            amm.updateClean(targetBranch);
            amm.mergeWorkspaceWith(featureBranch, null, "[Jenkins Integration Merge] Merged " + featureBranch + " into "
                    + targetBranch,
                    commitUsername);
            LogMessageSearcher.logMessage(listener, "Gatekeeper merge merged " +
                    featureBranch + " to " + targetBranch + ".");
        }
        commit(amm, listener, envVars, targetBranch, featureBranch, commitUsername);
        return true;
    }

    private void ensureReleaseBranch(AdvancedSCMManager amm, String targetBranch) throws AdvancedSCMException, ReleaseBranchInvalidException{
        String releaseFileContent = null;
        if (releaseFileContentTemplate != null && !releaseFileContentTemplate.isEmpty()
                && releaseFilePath != null && !releaseFilePath.isEmpty()) {
            Handlebars handlebars = new Handlebars();
            Context templateContext = Context.newContext(null);
            templateContext.data("release", amm.getReleaseBranch(targetBranch).getReleaseName());
            Template mustacheTemplate;
            try {
                mustacheTemplate = handlebars.compileInline(releaseFileContentTemplate);
                releaseFileContent = mustacheTemplate.apply(templateContext);
            } catch (IOException e) {
                throw new AdvancedSCMException("Error rendering release file content template");
            }
        }
        amm.ensureReleaseBranch(
                targetBranch, releaseFilePath, releaseFileContent,
                "[Jenkins Integration Merge] " + targetBranch + " release", commitUsername);
    }

    private void commit(AdvancedSCMManager amm, BuildListener listener, EnvVars envVars, String targetBranch, String featureBranch, String commitUsername) throws AdvancedSCMException {
        if (amm.getBranchNames(false).contains(featureBranch)) {
            // we have to close feature branch
            amm.closeBranch(featureBranch, "[Jenkins Integration Merge] Closing feature branch " + featureBranch, commitUsername);
            amm.updateClean(targetBranch);
        }
        LogMessageSearcher.logMessage(listener, "Gatekeeper merge was committed, because tests seem to be successful.");
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

