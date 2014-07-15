package org.paylogic.jenkins.advancedscm.backends.helpers;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.plugins.git.GitSCM;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;

import java.io.File;

/**
 * Created by bubenkoff on 4/22/14.
 */
public class AdvancedCliGit extends CliGitAPIImpl{

    protected transient Launcher launcher;

    public AdvancedCliGit(GitSCM scm, Launcher launcher, Node node, File workspace,
                            TaskListener listener, EnvVars environment) {
        super(scm.getGitExe(node, environment, listener), workspace, listener, environment);
        this.launcher = launcher;
    }
}
