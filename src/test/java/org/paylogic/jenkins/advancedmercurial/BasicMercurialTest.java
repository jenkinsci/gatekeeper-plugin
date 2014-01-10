package org.paylogic.jenkins.advancedmercurial;

import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.paylogic.jenkins.advancedscm.AdvancedSCMManager;
import org.paylogic.jenkins.advancedscm.SCMManagerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class BasicMercurialTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(j);
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
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with release and feature branch.
        m.hg(repo, "init");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "c3");
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
        scmList.add(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));
        scmList.add(new MercurialSCM(null, repo.getPath(), "tip", null, "src/asdf", null, false));

        p.setScm(new MultiSCM(scmList));

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("REPO_SUBDIR", "src/asdf"));

        // Init repo with release and feature branch.
        m.hg(repo, "init");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "c3");
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
        m.buildAndCheck(p, "src/asdf/c3", new ParametersAction(parameters));
    }
}