package org.paylogic.jenkins.advancedscm.backends.helpers;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Created by bubenkoff on 4/22/14.
 */
public class AdvancedCliGit extends CliGitAPIImpl{

    public AdvancedCliGit(GitSCM scm, Launcher launcher, Node node, File workspace,
                            TaskListener listener, EnvVars environment) {
        super(scm.getGitExe(node, environment, listener), workspace, listener, environment);
        try {
            Field field = CliGitAPIImpl.class.getDeclaredField("launcher");
            field.setAccessible(true);
            field.set(this, launcher);
        } catch (NoSuchFieldException exception) {
        }
        catch (IllegalAccessException exception) {
            
        }
    }
}
