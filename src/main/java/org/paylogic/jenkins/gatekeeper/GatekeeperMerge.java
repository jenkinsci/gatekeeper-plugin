package org.paylogic.jenkins.gatekeeper;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import lombok.Getter;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.jenkins.advancedmercurial.AdvancedMercurialManager;
import org.paylogic.jenkins.fogbugz.FogbugzNotifier;
import org.paylogic.redis.RedisProvider;
import redis.clients.jedis.Jedis;

import javax.swing.text.html.HTML;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeeper merge.
 */
@Log
public class GatekeeperMerge extends Builder {

    @Getter private final boolean doGatekeeping;

    @DataBoundConstructor
    public GatekeeperMerge(boolean doGatekeeping) {
        this.doGatekeeping = doGatekeeping;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Gatekeeper merge -----------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeep merge.", e);
            l.append("Exception occured, build aborting...");
            l.append(e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        EnvVars envVars = build.getEnvironment(listener);
        String givenCaseId = Util.replaceMacro("$CASE_ID", envVars);
        String featureBranch = envVars.get("FEATURE_BRANCH", "");
        String targetBranch = envVars.get("TARGET_BRANCH", "");
        String repo_path = envVars.get("REPO_PATH", "");
        int usableCaseId = Integer.parseInt(givenCaseId);

        AdvancedMercurialManager amm = new AdvancedMercurialManager(build, launcher, listener);

        if (repo_path == "" | targetBranch == "" | featureBranch == "") {
            /* Fetch branch information from Fogbugz */
            FogbugzCase fallbackCase = new FogbugzNotifier().getFogbugzCaseManager().getCaseById(usableCaseId);
            repo_path = fallbackCase.getFeatureBranch().split("#")[0];
            featureBranch = fallbackCase.getFeatureBranch().split("#")[1];
            envVars.override("FEATURE_BRANCH", featureBranch);
            targetBranch = fallbackCase.getTargetBranch();
            envVars.override("TARGET_BRANCH", targetBranch);
        }

        /* Actual Gatekeepering logic. Seperated to work differently when Rietveld support is active. */
        boolean runNormalMerge = this.getDescriptor().getUrl().isEmpty();
        if (!runNormalMerge) {
            // Use Rietveld support.
            String featureRepoUrl = this.getDescriptor().getRepoBase() + repo_path;  // Should produce correct url.

            String rietveldUrl = this.getDescriptor().getUrl() + "/get_latest_ok_for_case/" + Integer.toString(usableCaseId)  + "/";
            URL uri = new URL(rietveldUrl);
            HttpURLConnection con = (HttpURLConnection) uri.openConnection();
            if (con.getResponseCode() != 200) {
                log.log(Level.SEVERE, "Error while fetching latest OK revision from Rietveld.\n");
                listener.getLogger().append("Connected to: " + rietveldUrl + "\n");

                if (con.getResponseCode() == 404) {
                    listener.getLogger().append("Build was aborted because the case is not approved yet.\n");
                } else if (con.getResponseCode() == 500) {
                    listener.getLogger().append("Build was aborted because the case does not exist in CodeReview.\n");
                }

                throw new Exception("Error while fetching latest OK revision from Rietveld. Response code: " +
                        Integer.toString(con.getResponseCode()));
            }

            StringWriter sw = new StringWriter();
            IOUtils.copy(con.getInputStream(), sw, "UTF-8");
            String okRevision = sw.toString().replace("\n", "");
            listener.getLogger().append("Trying to merge with revision '" + okRevision + "' which was fetched from Rietveld.\n");
            listener.getLogger().append("Which should be in repo '" + featureRepoUrl + "', which we will pull.\n");

            amm.pull(featureRepoUrl);
            amm.update(targetBranch);
            amm.mergeWorkspaceWith(okRevision);
        }
        else {
            amm.pull();
            amm.update(targetBranch);
            amm.mergeWorkspaceWith(featureBranch);
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

