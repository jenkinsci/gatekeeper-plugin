package org.paylogic.jenkins.gatekeeper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import lombok.extern.java.Log;
import org.kohsuke.stapler.DataBoundConstructor;
import org.paylogic.jenkins.LogMessageSearcher;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;

import java.io.PrintStream;
import java.util.logging.Level;

/**
 * Created by bubenkoff on 20.12.13.
 */
@Log
public class GatekeeperPush extends Builder {

    @DataBoundConstructor
    public GatekeeperPush() {
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("------------------- Gatekeeper push --------------------");
        l.println("----------------------------------------------------------");
        try {
            return this.doPerform(build, launcher, listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during Gatekeeeper push.", e);
            l.append("Exception occured, build aborting...\n");
            LogMessageSearcher.logMessage(listener, e.toString());
            return false;
        }
    }

    private boolean doPerform(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        /* Set up enviroment and resolve some variables. */
        AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);
        EnvVars envVars = build.getEnvironment(listener);
        String branches_to_push = envVars.get("BRANCHES_TO_PUSH", "");
        amm.push(branches_to_push.split(","));
        LogMessageSearcher.logMessage(listener, "Gatekeeper push is done.");
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
            return "Perform Gatekeeper push.";
        }

        public DescriptorImpl() {
            super();
            load();
        }
    }
}
