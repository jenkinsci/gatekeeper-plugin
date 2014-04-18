package org.paylogic.jenkins.advancedscm;

import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;

/**
 * Container for branches. Contains branchname, revision number and hash of branch.
 * Git does not contain revision number, and subversion will not contain hash.
 * Objects can be obtained with the 'MercurialBackend instances'.
 */
public class Branch {
    @Getter private String branchName;
    @Getter private int revision;
    @Getter private String hash;

    public Branch(String branchName, @Nullable Integer revision, @Nullable String hash) {
        this.branchName = branchName;
        this.revision = revision;
        this.hash = hash;
    }
}
