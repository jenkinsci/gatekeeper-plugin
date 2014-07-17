/**
 * This file was copied from https://github.com/jenkinsci/mercurial-plugin/raw/master/src/test/java/hudson/plugins/mercurial/MercurialRule.java
 * so we as well have a MercurialRule to create test repos with.
 * The file is licensed under the MIT License, which can by found at: http://www.opensource.org/licenses/mit-license.php
 * More information about this file and it's authors can be found at: https://github.com/jenkinsci/mercurial-plugin/
 */

package org.paylogic.jenkins.advancedscm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitTagAction;
import hudson.scm.PollingResult;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Assume;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.JenkinsRule;
import org.paylogic.jenkins.ABuildCause;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Collections.sort;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public final class GitRule extends ExternalResource {

    private TaskListener listener;

    private final JenkinsRule j;

    public GitRule(JenkinsRule j) {
        this.j = j;
    }

    @Override protected void before() throws Exception {
        listener = new StreamTaskListener(System.out, Charset.defaultCharset());
        try {
            if (new ProcessBuilder("git", "--version").start().waitFor() != 0) {
                throw new AssumptionViolatedException("git --version signaled an error");
            }
        } catch(IOException ioe) {
            String message = ioe.getMessage();
            if(message.startsWith("Cannot run program \"git\"") && message.endsWith("No such file or directory")) {
                throw new AssumptionViolatedException("git is not available; please check that your PATH environment variable is properly configured");
            }
            Assume.assumeNoException(ioe); // failed to check availability of hg
        }
    }

    private Launcher launcher() {
        return j.jenkins.createLauncher(listener);
    }

    public GitClient gitClient(File repository) throws Exception {
        return new Git(listener, new EnvVars()).in(repository).using("git").getClient();
    }

    public void allowPush(GitClient client) throws InterruptedException{
        ((CliGitAPIImpl)client).launchCommand("config", "receive.denyCurrentBranch", "ignore");
    }

    public void touchAndCommit(File repo, String... names) throws Exception {
        GitClient client = gitClient(repo);
        for (String name : names) {
            FilePath toTouch = new FilePath(repo).child(name);
            if (!toTouch.exists()) {
                toTouch.getParent().mkdirs();
                toTouch.touch(0);
                client.add(name);
            } else {
                toTouch.write(toTouch.readToString() + "extra line\n", "UTF-8");
            }
        }
        client.commit("added " + Arrays.toString(names));
    }

    public String buildAndCheck(FreeStyleProject p, String name,
            Action... actions) throws Exception {
        FreeStyleBuild b = p.scheduleBuild2(0, new ABuildCause(), actions).get();
        assert b.getResult() == Result.SUCCESS;
        if (!b.getWorkspace().child(name).exists()) {
            Set<String> children = new TreeSet<String>();
            for (FilePath child : b.getWorkspace().list()) {
                children.add(child.getName());
            }
            fail("Could not find " + name + " among " + children);
        }
        assertNotNull(b.getAction(GitTagAction.class));
        @SuppressWarnings("deprecation")
        String log = b.getLog();
        return log;
    }

    public PollingResult pollSCMChanges(FreeStyleProject p) {
        return p.poll(new StreamTaskListener(System.out, Charset
                .defaultCharset()));
    }

    public String getLastChangesetId(File repo) throws Exception {
        return gitClient(repo).revParse("HEAD").name();
    }

    public String[] getBranches(File repo) throws Exception {
        ArrayList<String> list = new ArrayList<String>();
        for (Branch branch: gitClient(repo).getBranches()) {
            String[] parts = branch.getName().split("/");
            list.add(parts[parts.length - 1]);
        }
        sort(list);
        return list.toArray(new String[list.size()]);
    }

    public String searchLog(File repo, String query) throws Exception {
        String sw = ((CliGitAPIImpl)gitClient(repo)).launchCommand("log");
        StringWriter output = new StringWriter();
        for (String s: sw.split("\n")) {
            if (s.contains(query)) {
                output.append("s");
            }
        }
        String out = output.toString();
        assert !out.isEmpty();
        return out;
    }

}
