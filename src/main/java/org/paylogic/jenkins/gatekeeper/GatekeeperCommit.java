package org.paylogic.jenkins.gatekeeper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import lombok.Getter;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.jenkins.advancedmercurial.AdvancedMercurialManager;
import org.paylogic.redis.RedisProvider;
import redis.clients.jedis.Jedis;

import java.io.PrintStream;
import java.util.logging.Level;

/**
 * Created by bubenkoff on 20.12.13.
 */
@Log
public class GatekeeperCommit extends Builder {

    @Getter
    private final boolean doGatekeeping;

    @DataBoundConstructor
    public GatekeeperCommit(boolean doGatekeeping) {
        this.doGatekeeping = doGatekeeping;
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
            l.append("Exception occured, build aborting...");
            l.append(e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        EnvVars envVars = build.getEnvironment(listener);
        String targetBranch = Util.replaceMacro("$TARGET_BRANCH", envVars);
        String featureBranch = Util.replaceMacro("$FEATURE_BRANCH", envVars);

        AdvancedMercurialManager amm = new AdvancedMercurialManager(build, launcher, listener);
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
            return "Perform Gatekeeper commit.";
        }

        public DescriptorImpl() {
            super();
            load();
        }
    }
}
