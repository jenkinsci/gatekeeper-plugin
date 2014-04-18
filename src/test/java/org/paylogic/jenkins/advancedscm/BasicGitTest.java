package org.paylogic.jenkins.advancedscm;

import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class BasicGitTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public GitRule m = new GitRule(j);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public TemporaryFolder tmp2 = new TemporaryFolder();
    private File repo;
    private File repo2;

    @Before
    public void setUp() throws Exception {
        repo = tmp.getRoot();
        repo2 = tmp2.getRoot();
    }

    @Test
    public void testBasicMerge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo.getPath(), "test", "master", null));
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("master"));
        p.setScm(new GitSCM(remotes, branches, false, null, null, null, null));

        // Init repo with release and feature branch.
        GitClient client = m.gitClient(repo);
        client.init();
        m.touchAndCommit(repo, "init");
        client.checkoutBranch("r1336", "HEAD");
        m.touchAndCommit(repo, "r1336");
        client.checkoutBranch("c3", "HEAD");
        m.touchAndCommit(repo, "c3");

        // Custom builder that merges feature branch with release branch using AdvancedSCMManager.
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                try {
                    AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);
                    amm.update("r1336");
                    amm.mergeWorkspaceWith("c3");
                    amm.commit("Merge test!", "TestRunner");

                    return true;
                } catch (Exception e) {
                    e.printStackTrace(listener.getLogger());
                    return false;
                }
            }
        });

        // Assert file is here (should be after successful merge)
        m.buildAndCheck(p, "c3");
    }

    @Test
    public void testBasicMultiSCMMerge() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        ArrayList<SCM> scmList = new ArrayList<SCM>();

        List<UserRemoteConfig> remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo.getPath(), "test", "master", null));
        scmList.add(new GitSCM(remotes, null, false, null, null, null, null));

        remotes = new ArrayList<UserRemoteConfig>();
        remotes.add(new UserRemoteConfig(repo2.getPath(), "test", "master", null));
        List<GitSCMExtension> extensions = new ArrayList<GitSCMExtension>();
        extensions.add(new RelativeTargetDirectory("src/asdf"));
        scmList.add(new GitSCM(remotes, null, false, null, null, null, null));

        p.setScm(new MultiSCM(scmList));

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("REPO_SUBDIR", "src/asdf"));

        // Init repo with release and feature branch.
        GitClient client = m.gitClient(repo);
        client.init();
        m.touchAndCommit(repo, "dummy");

        client = m.gitClient(repo2);
        client.init();
        m.touchAndCommit(repo2, "init");
        client.checkoutBranch("r1336", "HEAD");
        m.touchAndCommit(repo2, "r1336");
        client.checkoutBranch("c3", "HEAD");
        m.touchAndCommit(repo2, "c3");

        // Custom builder that merges feature branch with release branch using AdvancedSCMManager.
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                try {
                    AdvancedSCMManager amm = SCMManagerFactory.getManager(build, launcher, listener);
                    amm.update("r1336");
                    amm.mergeWorkspaceWith("c3");
                    amm.commit("Merge test!", "TestRunner");

                    return true;
                } catch (Exception e) {
                    e.printStackTrace(listener.getLogger());
                    return false;
                }
            }
        });

        // Assert file is here (should be after successful merge)
        m.buildAndCheck(p, "src/asdf/c3", new ParametersAction(parameters));
    }
}