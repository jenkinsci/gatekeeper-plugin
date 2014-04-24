package org.paylogic.jenkins.advancedscm.backends.helpers;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;

import java.io.File;

/**
 * Created by bubenkoff on 4/22/14.
 */
public class AdvancedCliGit extends CliGitAPIImpl{

    public AdvancedCliGit(GitSCM scm, File workspace,
                            TaskListener listener, EnvVars environment) {
        super(scm.resolveGitTool(listener).getGitExe(), workspace, listener, environment);
    }
}
