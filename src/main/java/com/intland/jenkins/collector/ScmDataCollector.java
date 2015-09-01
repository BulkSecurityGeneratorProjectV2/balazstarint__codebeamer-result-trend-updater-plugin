/*
 * Copyright (c) 2015 Intland Software (support@intland.com)
 */

package com.intland.jenkins.collector;

import com.intland.jenkins.api.CodebeamerApiClient;
import com.intland.jenkins.collector.dto.ScmDto;
import com.intland.jenkins.util.PluginUtil;
import hudson.model.AbstractBuild;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.scm.ChangeLogSet;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionTagAction;
import jenkins.model.Jenkins;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScmDataCollector {
    private static final Pattern scmTaskIdPattern = Pattern.compile("(#([1-9][0-9]{3,9})((,|\\s+)[1-9][0-9]{3,9})*)(?:\\z|[\\s.,;:)/\\-]+)");

    public static ScmDto collectScmData(AbstractBuild<?, ?> build, CodebeamerApiClient apiClient) throws IOException {
        String repositoryLine = "Unsupported SCM";
        String changes = "";

        //Subversion in bundled by default, don't need to check plugin
        SubversionTagAction svnScm = build.getAction(SubversionTagAction.class);

        if (PluginUtil.isGitPluginInstalled() && build.getAction(BuildData.class) != null) {
            BuildData gitScm = build.getAction(BuildData.class);
            String repoUrl = (String)(gitScm.getRemoteUrls()).toArray()[0];
            String repoRevision = gitScm.getLastBuiltRevision().getSha1String();
            String repoBranchName =  ((List<Branch>) gitScm.getLastBuiltRevision().getBranches()).get(0).getName();
            repositoryLine = String.format("[%s], %s, branch: %s", repoUrl, repoRevision, repoBranchName);
        } else if (PluginUtil.isMercurialPluginInstalled() && build.getAction(MercurialTagAction.class) != null) {
            MercurialTagAction hgScm = build.getAction(MercurialTagAction.class);
            repositoryLine = hgScm.getId();
        } else if (svnScm != null) {
            SubversionSCM.SvnInfo svnInfo = new ArrayList<SubversionSCM.SvnInfo>(svnScm.getTags().keySet()).get(0);
            String repoUrl = "";
            try {
                repoUrl = svnInfo.getSVNURL().getURIEncodedPath();
            } catch (SVNException e) {
                e.printStackTrace();
            }
            String repoRevision = String.valueOf(svnInfo.revision);
            repositoryLine = String.format("[%s], %s", repoUrl, repoRevision);
        }

        for (ChangeLogSet.Entry entry : build.getChangeSet()) {
            String author = entry.getAuthor().toString();
            String userId = apiClient.getUserId(author);
            String commitMessage = getCodebeamerTaskLink(entry.getMsg());
            String formattedUser = userId == null ? String.format("(%s)", author) : String.format("([USER:%s])", userId);

            changes += String.format("* %s %s\\n", commitMessage, formattedUser);
        }

        return new ScmDto(repositoryLine, changes);
    }

    private static String getCodebeamerTaskLink(String gitCommitMessage) {
        Matcher commitMessageMatcher = scmTaskIdPattern.matcher(gitCommitMessage);
        String result = gitCommitMessage;

        List<String> issues = new ArrayList<String>();
        while (commitMessageMatcher.find()) {
            issues.add(commitMessageMatcher.group(1));
        }

        if (issues.size() > 0) {
            for (String issue : issues) {
                String link = String.format("[%s|ISSUE:%s]", issue, issue.replace("#",""));
                result = result.replace(issue, link);
            }
        }

        return result;
    }
}
