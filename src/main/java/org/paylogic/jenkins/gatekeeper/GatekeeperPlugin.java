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
import org.kohsuke.stapler.DataBoundConstructor;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.jenkins.advancedmercurial.AdvancedMercurialManager;
import org.paylogic.jenkins.fogbugz.FogbugzNotifier;
import org.paylogic.redis.RedisProvider;
import redis.clients.jedis.Jedis;

import java.io.PrintStream;
import java.util.logging.Level;


/**
 * Main extension of the GatekeeperPlugin. Does Gatekeepering.
 */
@Log
public class GatekeeperPlugin extends Builder {

    @Getter private final boolean doGatekeeping;

    @DataBoundConstructor
    public GatekeeperPlugin(boolean doGatekeeping) {
        this.doGatekeeping = doGatekeeping;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Now Gatekeepering --------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeepring.", e);
            l.append("Exception occured, build aborting...");
            l.append(e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        EnvVars envVars = build.getEnvironment(listener);
        String givenCaseId = Util.replaceMacro("$CASE_ID", envVars);
        int usableCaseId = Integer.parseInt(givenCaseId);

        AdvancedMercurialManager amm = new AdvancedMercurialManager(build, launcher, listener);

        /* Fetch branch information from Fogbugz */
        FogbugzCase fallbackCase = new FogbugzNotifier().getFogbugzCaseManager().getCaseById(usableCaseId);
        String featureBranch = fallbackCase.getFeatureBranch().split("#")[1];
        String targetBranch = fallbackCase.getTargetBranch();

        /* Actual Gatekeepering logic. */
        amm.pull();
        amm.update(targetBranch);
        amm.mergeWorkspaceWith(featureBranch);
        amm.commit("[Jenkins Integration Merge] Merge " + targetBranch + " with " + featureBranch);

        /* Set the featureBranch we merged with in redis to other plugin know this was actually the build's branch */
        RedisProvider redisProvider = new RedisProvider();
        Jedis redis = redisProvider.getConnection();
        redis.set("old_" + build.getExternalizableId(), featureBranch);

        /* Add commit to list of things to push. */
        redis.rpush("topush_" + build.getExternalizableId(), targetBranch);
        redisProvider.returnConnection(redis);
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
            return "Perform Gatekeerping.";
        }
    }
}

