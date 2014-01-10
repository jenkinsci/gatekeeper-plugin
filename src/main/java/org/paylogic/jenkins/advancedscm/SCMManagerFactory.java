package org.paylogic.jenkins.advancedscm;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import lombok.extern.java.Log;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.paylogic.jenkins.advancedscm.backends.GitBackend;
import org.paylogic.jenkins.advancedscm.backends.MercurialBackend;

import java.io.PrintStream;
import java.util.List;

/**
 * Factory for AdvancedSCMManager derived objects.
 * Automagically chooses which backend implementation you need,
 * and even assigns the correct multiscm repo to that implementation if you use multiscm.
 */
@Log
public class SCMManagerFactory {
    public static AdvancedSCMManager getManager(AbstractBuild build, Launcher launcher, BuildListener listener) throws Exception {
        String givenRepoSubdir = null;
        PrintStream l = listener.getLogger();
        givenRepoSubdir = build.getEnvironment(listener).get("REPO_SUBDIR", "");
        SCM scm = build.getProject().getScm();

        // Sort out multiscm scms.
        if (scm instanceof MultiSCM) {
            List<SCM> scms = ((MultiSCM) scm).getConfiguredSCMs();
            if (scms.size() > 1) {
                // loop and find correct repo to apply credentials on
                for (SCM s: ((MultiSCM) scm).getConfiguredSCMs()) {
                    // Only if typeof scm is mercurial
                    if (s instanceof MercurialSCM) {
                        String subDir = ((MercurialSCM) s).getSubdir();
                        if (subDir != null) {
                            if (subDir.equals(givenRepoSubdir)) {
                                l.append("Chosen MultiSCM with Mercurial Backend");
                                return new MercurialBackend(build, launcher, listener, (MercurialSCM) s);
                            }
                        }
                    } else if (s instanceof GitSCM) {
                        String subDir = ((GitSCM) s).getRelativeTargetDir(); // TODO: non-deprecated version
                        if (subDir != null) {
                            if (subDir.equals(givenRepoSubdir)) {
                                l.append("Chosen MutliSCM with Git Backend");
                                return new GitBackend(build, launcher, listener, (GitSCM) s);
                            }
                        }
                    }
                }
            } else {
                scm = scms.get(0);
            }
        }

        // No multiscm, just return correct backend.
        if (scm instanceof MercurialSCM) {
            l.append("Chosen Mercurial backend, NO MultiSCM");
            return new MercurialBackend(build, launcher, listener, (MercurialSCM) scm);
        } else if (scm instanceof GitSCM) {
            l.append("Chosen Git backend, NO MultiSCM");
            return new GitBackend(build, launcher, listener, (GitSCM) scm);
        }

        // If we come here, no viable SCM was found, so we quit.
        throw new Exception("There is no implementation available for the chosen SCM. Sorry about that.");
    }
}
