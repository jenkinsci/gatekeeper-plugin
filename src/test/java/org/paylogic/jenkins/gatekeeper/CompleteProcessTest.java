package org.paylogic.jenkins.gatekeeper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.paylogic.jenkins.advancedmercurial.MercurialRule;

import java.io.File;


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

    public void testProcessWithoutFogbugz() throws Exception {

    }

    public void testProcessWithFogbugz() throws Exception {

    }

    public void testProcessWithFogbugzAndPullFromDifferentRepo() throws Exception {

    }
}
