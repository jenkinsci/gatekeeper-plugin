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
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.jenkinsci.plugins.envinject.EnvInjectBuilderContributionAction;

import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.jenkins.advancedmercurial.advancedmercurial.AdvancedMercurialManager;
import org.paylogic.jenkins.advancedmercurial.advancedmercurial.exceptions.MercurialMergeConflictException;
import org.paylogic.jenkins.fogbugz.FogbugzNotifier;
import org.paylogic.jenkins.fogbugz.LogMessageSearcher;

import java.io.PrintStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeeper merge.
 */
@Log
public class GatekeeperMerge extends Builder {

    /**
     * This expression derived/taken from the BNF for URI (RFC2396).
     * Validates URLs
     */
    private static final String URL_REGEX = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

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
        } catch (MercurialMergeConflictException e) {
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
        int usableCaseId = 0;
        String givenCaseId = envVars.get("CASE_ID", "");
        if (givenCaseId != "") {
            usableCaseId = Integer.parseInt(givenCaseId);
        }

        String repo_path = envVars.get("REPO_PATH", "");

        if (targetBranch.isEmpty() | featureBranch.isEmpty()) {
            /* Fetch branch information from Fogbugz */
            FogbugzCase fallbackCase = new FogbugzNotifier().getFogbugzCaseManager().getCaseById(usableCaseId);
            repo_path = fallbackCase.getFeatureBranch().split("#")[0];
            featureBranch = fallbackCase.getFeatureBranch().split("#")[1];
            targetBranch = fallbackCase.getTargetBranch();
            envVars.override("FEATURE_BRANCH", featureBranch);
            envVars.override("TARGET_BRANCH", targetBranch);
            // Set the new build variables map
            build.addAction(new EnvInjectBuilderContributionAction(envVars));
        }

        AdvancedMercurialManager amm = new AdvancedMercurialManager(build, launcher, listener);

        amm.stripLocal();

        /* Actual Gatekeepering logic. Seperated to work differently when Rietveld support is active. */
        boolean runNormalMerge = this.getDescriptor().getUrl().isEmpty();
        if (!runNormalMerge) { // Use Rietveld support.

            String repoBase = this.getDescriptor().getRepoBase();
            Matcher baseUrlMatcher = URL_PATTERN.matcher(repoBase);
            Matcher repoUrlMatcher = URL_PATTERN.matcher(repo_path);
            String featureRepoUrl;

            // We seem to need to call these, otherwise 'group' method will not work.
            baseUrlMatcher.matches();
            repoUrlMatcher.matches();

            /* Logic for recognizing different formats or repository URLs */
            if (repoUrlMatcher.group(1) != null) {
                // If repo_path contains startswith one of: ssh:// http:// https://, we assume user put in full path and use that.
                featureRepoUrl = repo_path;

            } else if (repoUrlMatcher.group(5).contains(baseUrlMatcher.group(5).substring(1, baseUrlMatcher.group(5).length()))) {
                // if repo_path contains part of repoBase, this is probably in format /var/hg/repo#123, so we fix that.
                // strips first char from baseUrl group 5, which makes //var/hg /var/hg, or /var/hg var/hg, which makes the if match like we want.
                featureRepoUrl = baseUrlMatcher.group(1) + baseUrlMatcher.group(3);
                // If protocol is ssh, add extra /
                if (baseUrlMatcher.group(2).equals("ssh")) {
                    featureRepoUrl += "/";
                }
                featureRepoUrl += repo_path;

            } else {
                // else we construct path with base from settings. format probably is users/repo#123 or repo#123, so that works.
                featureRepoUrl = this.getDescriptor().getRepoBase() + repo_path;
            }

            // We test if the resulting URL is a valid one, if not, something went wrong and we quit.
            if (!featureRepoUrl.matches(URL_REGEX)) {
                throw new Exception("Error while constructing valid repository URL, we came up with " + featureRepoUrl);
            }

            log.log(Level.INFO, "Pulling from repository at: " + featureRepoUrl);

            /* Fetch latest OK revision from rietveld. */
            String rietveldUrl = this.getDescriptor().getUrl() + "/get_latest_ok_for_case/" + Integer.toString(usableCaseId)  + "/";
            URL uri = new URL(rietveldUrl);
            HttpURLConnection con = (HttpURLConnection) uri.openConnection();
            if (con.getResponseCode() != 200) {
                log.log(Level.SEVERE, "Error while fetching latest OK revision from Rietveld.\n");
                listener.getLogger().append("Connected to: " + rietveldUrl + "\n");

                if (con.getResponseCode() == 404) {
                    LogMessageSearcher.logMessage(listener, "Build was aborted because the case is not approved yet.");
                } else if (con.getResponseCode() == 500) {
                    LogMessageSearcher.logMessage(listener, "Build was aborted because the case does not exist in CodeReview.");
                }

                throw new Exception("Error while fetching latest OK revision from Rietveld. Response code: " +
                        Integer.toString(con.getResponseCode()));
            }

            StringWriter sw = new StringWriter();
            IOUtils.copy(con.getInputStream(), sw, "UTF-8");
            String okRevision = sw.toString().replace("\n", "");
            listener.getLogger().append("Trying to merge with revision '" + okRevision + "' which was fetched from Rietveld.\n");
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
        private String url;
        private String repoBase;

        public String getUrl() {
            if (this.url == null) {
                return "";
            } else {
                return this.url;
            }
        }

        public void setUrl(String url) {
            if (url == null) {
                this.url = "";
            } else {
                this.url = url;
            }
        }

        public String getRepoBase() {
            if (this.repoBase == null) {
                return "";
            } else {
                return this.repoBase;
            }
        }

        public void setRepoBase(String repoBase){
            if (repoBase == null) {
                this.repoBase = "";
            } else {
                this.repoBase = repoBase;
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Perform Gatekeeper merge.";
        }

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.setUrl(formData.getString("url"));
            this.setRepoBase(formData.getString("repoBase"));
            save();
            return super.configure(req, formData);
        }
    }
}

