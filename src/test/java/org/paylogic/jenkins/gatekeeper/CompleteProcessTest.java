package org.paylogic.jenkins.gatekeeper;

import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.plugins.mercurial.MercurialSCM;
import lombok.extern.java.Log;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.paylogic.jenkins.advancedmercurial.MercurialRule;
import org.paylogic.jenkins.upmerge.UpmergeBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;


@Log
public class CompleteProcessTest {

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
    public void testGatekeeperingAndUpmerging() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases and 1 feature branch
         * inject parameters TARGET_BRANCH and stuff
         * run job with both gatekeeper and upmerge tasks
         * assert file from feature branch is in latest release branch
         */
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with 3 releases and feature branch.
        m.hg(repo, "init");
        m.touchAndCommit(repo, "base");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "r1338");
        m.touchAndCommit(repo, "r1338");
        m.hg(repo, "branch", "r1340");
        m.touchAndCommit(repo, "r1340");
        m.hg(repo, "update", "r1336");
        m.hg(repo, "branch", "c3");
        m.touchAndCommit(repo, "c3");

        GatekeeperMerge mergeBuilder = new GatekeeperMerge();
        GatekeeperCommit commitBuilder = new GatekeeperCommit();
        UpmergeBuilder upmergeBuilder = new UpmergeBuilder();

        p.getBuildersList().add(mergeBuilder);
        p.getBuildersList().add(commitBuilder);
        p.getBuildersList().add(upmergeBuilder);

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));
        parameters.add(new StringParameterValue("COMMIT_USER_NAME", "JenkinsTestRunner"));

        m.buildAndCheck(p, "c3", new ParametersAction(parameters));
    }

    @Test
    public void testGatekeeperingFromDifferentRepoAndUpmerging() throws Exception {
        /*
         * So:
         * set up a repo with 3 releases
         * set up another repo which should somehow be a copy of this repo with a feature branch
         * inject APPROVED_REVISION, which is the latest revision of the feature brnach
         * inject parameters TARGET_BRANCH and stuff
         * run job with both gatekeeper and upmerge tasks
         * assert file from feature branch is in latest release branch
         */

        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new MercurialSCM(null, repo.getPath(), "tip", null, null, null, false));

        // Init repo with 3 releases and feature branch.
        m.hg(repo, "init");
        m.touchAndCommit(repo, "base");
        m.hg(repo, "branch", "r1336");
        m.touchAndCommit(repo, "r1336");
        m.hg(repo, "branch", "r1338");
        m.touchAndCommit(repo, "r1338");
        m.hg(repo, "branch", "r1340");
        m.touchAndCommit(repo, "r1340");

        // Clone to second repo
        m.hg(repo2, "clone", repo.getAbsolutePath(), ".");
        m.hg(repo2, "update", "r1336");
        m.hg(repo2, "branch", "c3");
        m.touchAndCommit(repo2, "c3");

        p.getBuildersList().add(new GatekeeperMerge());
        p.getBuildersList().add(new GatekeeperCommit());
        p.getBuildersList().add(new UpmergeBuilder());

        ArrayList<ParameterValue> parameters = new ArrayList<ParameterValue>();
        parameters.add(new StringParameterValue("TARGET_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("ORIGINAL_BRANCH", "r1336"));
        parameters.add(new StringParameterValue("FEATURE_BRANCH", "c3"));
        parameters.add(new StringParameterValue("APPROVED_REVISION", m.getLastChangesetId(repo2)));
        parameters.add(new StringParameterValue("REPO_URL", repo2.getAbsolutePath()));
        parameters.add(new StringParameterValue("COMMIT_USER_NAME", "JenkinsTestRunner"));

        m.buildAndCheck(p, "c3", new ParametersAction(parameters));
    }

}
