package org.paylogic.jenkins.advancedmercurial;

import lombok.Getter;

/**
 * Container for Mercurial branches. Contains branchname, revision number and hash of branch.
 * Objects can be obtained with the 'AdvancedMercurialManager'.
 */
public class MercurialBranch {
    @Getter private String branchName;
    @Getter private int revision;
    @Getter private String hash;

    public MercurialBranch(String branchName, int revision, String hash) {
        this.branchName = branchName;
        this.revision = revision;
        this.hash = hash;
    }
}
