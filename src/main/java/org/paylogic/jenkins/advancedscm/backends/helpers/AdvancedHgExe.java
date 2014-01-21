package org.paylogic.jenkins.advancedscm.backends.helpers;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.mercurial.HgExe;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AdvancedHgExe extends HgExe {

    private FilePath filePath;

    public static int DEFAULT_TIMEOUT = 360; // 6 minutes (time is in seconds)
    public static int DEFAULT_PUSH_TIMEOUT = 60 * 60 * 60; // one hour (time is in seconds)

    public AdvancedHgExe(MercurialSCM scm, Launcher launcher, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        super(scm, launcher, build, listener);
        FilePath path = build.getWorkspace();

        if (scm.getSubdir() != null && !scm.getSubdir().isEmpty()) {
            path = path.child(scm.getSubdir());
        }

        this.filePath = path;
    }

    /**
     * Runs the command and captures the output.
     */
    public String popen(FilePath repository, TaskListener listener, int timeout, ArgumentListBuilder args,
                        int[] returnCodes)
            throws IOException, InterruptedException {
        args = seed(false).add(args.toCommandArray());

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        if (ArrayUtils.contains(returnCodes,
                joinWithPossibleTimeout(launch(args).pwd(repository).stdout(data), timeout, listener))) {
            return data.toString();
        } else {
            // We override this because we don't want sensitive data in error logs.
            String command = "";
            for (String arg: args.toList()) {
                if (!arg.contains("auth") || !arg.contains("ssh")) {
                    command += arg;
                } else {
                    command += "********";
                }
                command += " ";
            }
            listener.error("Failed to run " + command);
            listener.getLogger().write(data.toByteArray());
            throw new AbortException(data.toString());
        }
    }

    /**
     * For use with {@link #launch} (or similar) when running commands not inside a build and which therefore might not be easily killed.
     */
    public static int joinWithPossibleTimeout(Launcher.ProcStarter proc, int timeout, final TaskListener listener) throws IOException, InterruptedException {
        return timeout != 0 ? proc.start().joinWithTimeout(timeout, TimeUnit.SECONDS, listener) : proc.start().joinWithTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS, listener);
    }


    public String popen(FilePath repository, TaskListener listener, int timeout, ArgumentListBuilder args)
            throws IOException, InterruptedException {
        int[] returnCodes = {0};
        return popen(repository, listener, timeout, args, returnCodes);
    }

    public @CheckForNull String branch() throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder("branch"));
        listener.getLogger().append(output);
        return output;
    }

    public @CheckForNull String branches() throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder("branches"));
        return output;
    }

    public String update(String revision) throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder("update", revision));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String updateClean(String revision) throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder("update", "-C", revision));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String clean() throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder(
                "--config", "extensions.purge=", "purge", "--all"));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    private static final String[] EMPTY = {};

    public String[] out() throws IOException, InterruptedException {
        int [] returnCodes = {0, 1};
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder(
                "out", "-q", "--template", "{rev}:"), returnCodes);
        if (StringUtils.isEmpty(output)) {
            return EMPTY;
        }
        return output.split(":");
    }

    public String commit(String message, String username) throws IOException, InterruptedException {
        int [] returnCodes = {0, 1};
        String output = popen(
                this.filePath, listener, 0, new ArgumentListBuilder(
                "--config", "ui.username=" + username,
                "commit", "-m", message), returnCodes);
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String merge(String revision) throws IOException, InterruptedException {
        int [] returnCodes = {0, 255};
        String output = popen(this.filePath, listener, 0, new ArgumentListBuilder("merge", "--tool", "internal:merge", revision), returnCodes);
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String push(String... extraArgs) throws IOException, InterruptedException {
        ArgumentListBuilder builder = new ArgumentListBuilder("push");
        for(String item : extraArgs){
            builder.add(item);
        }
        String output = popen(this.filePath, listener, DEFAULT_PUSH_TIMEOUT, builder);
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String strip(String[] extraArgs) throws IOException, InterruptedException {
        ArgumentListBuilder builder = new ArgumentListBuilder("--config", "extensions.strip=", "strip");
        for(String item : extraArgs){
            builder.add(item.trim());
        }
        String output = popen(this.filePath, listener, 0, builder);
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String pullChanges() throws IOException, InterruptedException {  // This has a wheird name because of extended class.
        String output = popen(this.filePath, listener, DEFAULT_PUSH_TIMEOUT, new ArgumentListBuilder("pull"));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String pullChanges(String otherRepo) throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, DEFAULT_PUSH_TIMEOUT, new ArgumentListBuilder("pull", otherRepo));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }

    public String pullChanges(String otherRepo, String branch) throws IOException, InterruptedException {
        String output = popen(this.filePath, listener, DEFAULT_PUSH_TIMEOUT, new ArgumentListBuilder(
                "pull", otherRepo, "-b", branch));
        if (StringUtils.isEmpty(output)) {
            return "";
        }
        listener.getLogger().append(output);
        return output;
    }
}
