package org.msu.mi.teva.github.util

import groovy.sql.Sql
import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit

/**
 * Created by josh on 5/19/14.
 */

@Log4j
class GithubMiner {


    def repoCache = "/Users/josh/TMP/githubrepos"
    Sql sql
    int repoId
    def repoName, repoOwner
    Map<Integer, PullRequest> prMap = [:]
    Map<Integer, Integer> prNumberMap = [:]
    Map<Issue, Set<Issue>> issueMap = [:]
    Map<String, Commit> commits = [:]


    public GithubMiner(Sql s, int repoId) {
        this.sql = s
        this.repoId = repoId
        configureRepo()
        log.info("Working with repo $repoOwner:$repoName")
    }


    def getCommitsFromRepo() {

        File f = new File("$repoCache/$repoOwner/$repoName")
        if (!f.exists()) {
            f.mkdirs()
            CloneCommand clone = Git.cloneRepository()
            clone.setURI("https://github.com/$repoOwner/${repoName}.git")
            clone.setDirectory(f)
            //assumes we have a master!


            clone.setBranch("master")
            clone.call()
        }
        Git git = Git.open(f);
        for (RevCommit commit : git.log().call()) {
            commits[commit.id.name] = new Commit(commit.id.name, commit.authorIdent.name, commit.authorIdent.emailAddress, commit.authorIdent.when)
        }
    }


    def configureRepo() {
        def result = sql.firstRow("select projects.name, users.login from projects inner join users on owner_id = users.id  where projects.id = $repoId" as String);
        repoName = result.name
        repoOwner = result.login
    }

    @TupleConstructor
    private class Commit {

        def sha
        def authorName
        def authorEmail
        def time

    }


    private class PullRequest {

        def issues = []
        def prIssueComments = 0
        def prIssueCommenters = []

        def prCodeComments = 0
        def prCodeCommenters = []

        int nthRequestThisRepo

        int commits


        def owner = null
        def gitHubMerger
        def mergedByCommitsInMaster = false
        def mergedByFixesInCommit = false
        def mergedByShaInComments = false
        def mergedByMergedInComments = false


        def id
        Date createDate
        Date mergedDate

        int files
        int additions
        int deletions

        Float cpf

        def subscribedToReferenced = [] as Set
        def assignedToReferenced = [] as Set

        def refsFromCommits = [] as Set
        def refsFromPulls = [] as Set


        def ownerCommentedOnIssueBefore
        def ownerCommentedOnPullBefore

        def ownerSubscribedToOthers
        def ownerAssignedToOthers

        def ownerClosedOtherIssues
        def ownerClosedOtherPulls
        def ownerReopenedOthers
        def ownerMergedOthers
        def ownerReferencedOthers
        def ownerMentionedInOthers


        def ownerCommentedOnCommits

        def ownerCreatedOtherIssues


        public PullRequest(def result) {

            issues = new ArrayList<String>();
            commits = result.pr_commits
            owner = result.owner_login
            id = result.pr_id
            gitHubMerger = result.merger_login
            createDate = new Date(result.pr_created_at.time)
            mergedDate = result.merged_at ? new Date(result.merged_at.time) : null


            files = result.files ?: 0
            additions = result.additions ?: 0
            deletions = result.deletions ?: 0

            cpf = (files ? (additions + deletions) / files : 0) as Float

        }


        public List<PullRequest> bundle(boolean really) {
            //((multiPr&&really)?{(it.priorPr?[it]+call(it.priorPr):[it])}(this):[this])
            [this]
        }

        boolean isMerged() {
            gitHubMerger || mergedByCommitsInMaster || mergedByFixesInCommit || mergedByMergedInComments || mergedByShaInComments
        }


    }

    private class Issue {

        def issueOwner
        def isPull


        def issueResolvedId
        def issueCreated


        def comments = []


        public Issue(def result) {


            issueResolvedId = result.issue_id
            isPull = result.pull_request
            issueOwner = result.creator
            issueCreated = new Date(result.creation_time.time)


        }

        def addComments(def result) {
            if (result.commenter) {
                comments << new Comment(new Date(result.comment_time.time), result.commenter)
            }
        }

        public int hashCode() {
            return (issueResolvedId * 7) % 13

        }

        //NOTE:  Equals only holds up within projects
        public boolean equals(Object o) {
            return o.issueResolvedId == issueResolvedId
        }


    }

    def getBranchSelector() {
        (repoId == 107534?"pull_request_branches.`branch` in ('2.10.x','master')":"pull_request_branches.`branch` = 'master'")
    }

    def getPullRequests() {
        def query = "select pull_requests.id pr_id, pull_requests.`pullreq_id` pr_number, count(pull_request_commits.commit_id) pr_commits, issues.id issue_id, pr_user.login owner_login, " +
                "issues.created_at pr_created_at, merger_user.login merger_login, issue_events.created_at merged_at, pull_requests.head_commit_id, sum(commits.files) files, " +
                "sum(commits.additions) additions, sum(commits.`deletions`) deletions from pull_requests inner join pull_request_branches on " +
                "pull_requests.id = pull_request_branches.`pull_req_id` and ${getBranchSelector()} inner join users pr_user on pull_requests.user_id = pr_user.id " +
                "inner join issues on issues.pull_request_id = pull_requests.id and issues.reporter_id IS NULL left join issue_events on issue_events.issue_id = issues.id and issue_events.action='merged' " +
                "left join users merger_user on issue_events.actor_id = merger_user.id left join pull_request_commits on pull_requests.id = pull_request_commits.`pull_request_id` " +
                "left join commits on pull_request_commits.`commit_id` = commits.id and commits.`author_id` = pull_requests.`user_id` where base_repo_id = $repoId group by pr_id order by pr_created_at;"

        def ownerPrs = [:]
        sql.eachRow(query) {

            def pr = new PullRequest(it)
            prMap[pr.id] = pr
            prNumberMap[it.pr_number] = it.pr_id
            if (!ownerPrs[pr.owner]) {
                ownerPrs[pr.owner] = [pr]
            } else {
                ownerPrs[pr.owner] << pr
            }


            pr.nthRequestThisRepo = ownerPrs[pr.owner].size() - 1

        }


    }

    def checkExternalMerge() {
        getCommitsFromRepo()
        def fixre = /(?msi)(?:fixe[sd]?|close[sd]?|resolve[sd]?)(?:[^\/]*?|and)#([0-9]+)/
        sql.eachRow("select pull_request_commits.pull_request_id, commits.sha, commits_from_mongo.`message` from pull_request_commits inner join " +
                "pull_requests on pull_request_commits.pull_request_id=pull_requests.id inner join commits on commits.id = pull_request_commits.commit_id " +
                "inner join commits_from_mongo on commits.`ext_ref_id` = commits_from_mongo.`ext_ref_id` where pull_requests.base_repo_id = $repoId order by " +
                "pull_request_commits.pull_request_id" as String) {

            if (prMap[it.pull_request_id] && commits[it.sha] && !prMap[it.pull_request_id].merged) {

                prMap[it.pull_request_id].mergedByCommitsInMaster = true
            }

        }

        sql.eachRow("select commits_from_mongo.message from commits_from_mongo inner join commits on commits.ext_ref_id = commits_from_mongo.ext_ref_id where commits.project_id = $repoId" as String) {
            def m = it.message =~ fixre
            m.each {

                def prid = prNumberMap[it[1] as Integer]
                if (prid && prMap[prid] && !prMap[prid].merged) {
                    prMap[prid].mergedByFixesInCommit = true
                }
            }

        }
        def prid = null;
        def count = 0;
        def shamatch = /(?msi)([0-9a-f]{6,40})/
        def langmatch1 = /(?msi)(?:merg|pull|push|integrat)(?:ing|ed)/
        def langmatch2 = /(?msi)(?:apply)(?:ing|ed)/

        sql.eachRow("select pull_requests.id, users.login commenter, issue_comments.`created_at`, issue_comment_table.`comment_text` from issues " +
                "inner join pull_requests on pull_requests.id = issues.pull_request_id left join issue_comments on issues.id = issue_comments.issue_id " +
                "inner join users on issue_comments.user_id = users.id inner join issue_comment_table on issue_comments.comment_id = issue_comment_table.id " +
                "where pull_requests.base_repo_id = $repoId order by pull_requests.id, issue_comments.created_at desc") { row ->

            if (prMap[row.id] && !prMap[row.id].merged) {
                if (prid != row.id) {
                    prid = row.id
                    count = 0
                }

                if (count++ < 3) {
                    if ((row.comment_text =~ shamatch).find {
                        commits[it[1]] && !prMap[row.id].merged
                    }) prMap[row.id].mergedByShaInComments = true
                    else if ((row.comment_text =~ langmatch1).matches() || (row.comment_text =~ langmatch2).matches()) prMap[row.id].mergedByMergedInComments = true

                }

            }


        }


    }


    public class Comment {

        def time
        def owner

        public Comment(Date time, String owner) {
            this.time = time
            this.owner = owner
        }

    }


    def markupIssueEvents() {
        def query = "select pull_requests.id, group_concat(distinct pull_request_refs.`issue_id`),  sum(IF(issue_events.action=\"subscribed\",1,0)) " +
                "subscribed, sum(IF(issue_events.action=\"assigned\",1,0)) assigned, sum(IF(issue_events.action=\"closed\",1,0)) closed_any, " +
                "sum(IF(issue_events.action=\"reopened\",1,0)) reopened, sum(IF(issue_events.action=\"merged\",1,0)) merged, sum(IF(issue_events.action=\"closed\" and " +
                "issues.`pull_request`,1,0)) closed_pr, sum(IF(issue_events.action=\"closed\" and !issues.`pull_request`,1,0)) closed_iss, " +
                "sum(IF(issue_events.action=\"referenced\",1,0)) referenced, sum(IF(issue_events.action=\"mentioned\",1,0)) mentioned from pull_requests inner " +
                "join pull_request_history on pull_requests.id = pull_request_history.`pull_request_id` and pull_request_history.`action` = \"opened\" inner " +
                "join issue_events on pull_requests.user_id = issue_events.`actor_id` and issue_events.`created_at` < pull_request_history.`created_at` " +
                "left join pull_request_refs on pull_requests.id = pull_request_refs.`pr_id` inner join issues on issue_events.`issue_id` = issues.id " +
                "where (ISNULL(pull_request_refs.issue_id) || pull_request_refs.issue_id != issues.id) and pull_requests.base_repo_id = $repoId group by pull_requests.id"

        sql.eachRow(query) {
            PullRequest req = prMap[it.id]
            if (req) {
                req.ownerSubscribedToOthers = (it.subscribed > 0)
                req.ownerAssignedToOthers = (it.assigned > 0)
                req.ownerClosedOtherIssues = (it.closed_iss > 0)
                req.ownerClosedOtherPulls = (it.closed_pr > 0)
                req.ownerReferencedOthers = (it.referenced > 0)
                req.ownerMentionedInOthers = (it.mentioned > 0)
                req.ownerMergedOthers = (it.merged > 0)
                req.ownerReopenedOthers = (it.reopened > 0)

            }
        }
    }


    def markupCreatedIssues() {
        def query = "select sum(!issues.`pull_request`) create_issues, sum(issues.`pull_request`) create_pulls, a.* from issues inner join " +
                "(select pull_requests.id pr_id, pull_request_history.`actor_id` creator, pull_request_history.`created_at`, pull_request_refs.issue_id " +
                "issue_ref from pull_requests inner join pull_request_history on pull_requests.id = pull_request_history.`pull_request_id` and pull_request_history.action='opened' " +
                "left join pull_request_refs on pull_requests.id = pull_request_refs.pr_id where pull_requests.`base_repo_id`= $repoId) a on issues.`reporter_id` = a.creator and " +
                "issues.`created_at` < a.created_at and (ISNULL(a.issue_ref) || issues.id != a.issue_ref) where issues.`repo_id` = $repoId group by a.pr_id;"

        sql.eachRow(query) {
            PullRequest req = prMap[it.pr_id]
            if (req) {
                req.ownerCreatedOtherIssues = (it.create_issues > 0)
            }
        }
    }


    def markupIssueCommentsBeforePull() {
        def query = "select pull_requests.id pr_id, users.login, pull_request_history.created_at pr_date, issue_comments.issue_id iss_id, issue_comments.comment_id, " +
                "issue_comments.created_at iss_commment_date, sum(issues.`pull_request`)>0 pull_comments, sum(!issues.pull_request)>0 not_pull_comments from pull_requests " +
                "left join pull_request_history on pull_requests.id = pull_request_history.`pull_request_id` and pull_request_history.`action` = \"opened\"  " +
                "inner join issue_comments on pull_requests.user_id = issue_comments.`user_id` and issue_comments.`created_at` < pull_request_history.`created_at` " +
                "inner join users on pull_requests.user_id = users.id  inner join issues on issues.id = issue_comments.issue_id left join " +
                "pull_request_refs on pull_request_refs.`issue_id` = issue_comments.issue_id and pull_request_refs.pr_id = pull_requests.id " +
                "where pull_requests.base_repo_id = $repoId and pull_request_refs.id IS NULL group by pull_requests.id;"


        sql.eachRow(query) {
            PullRequest pr = prMap[it.pr_id]
            if (pr) {

                pr.ownerCommentedOnIssueBefore = it.not_pull_comments
                pr.ownerCommentedOnPullBefore = it.pull_comments

            }
        }
    }

    def markupCommitComments() {
        def query = "select a.pr_id, count(1) prior_comments from commit_comments inner join commits on commit_comments.`commit_id` = commits.id " +
                "and commits.`project_id` = $repoId inner join (select pull_requests.id pr_id, pull_request_history.`actor_id` creator, " +
                "pull_request_history.`created_at` from pull_requests inner join pull_request_history on pull_requests.id = pull_request_history.`pull_request_id` and " +
                "pull_request_history.action='opened' where pull_requests.`base_repo_id`= $repoId) a on commit_comments.`created_at` < a.`created_at` and commit_comments.user_id = a.creator  group by a.pr_id;"


        sql.eachRow(query) {
            def PullRequest pr = prMap[it.pr_id]
            if (pr) {
                pr.ownerCommentedOnCommits = (it.prior_comments > 0)
            }
        }
    }


    def linkToReferencedIssues() {


        def query = "select pull_requests.id pr_id, pull_request_refs.`issue_id`, pull_request_refs.type, issues.`pull_request`, issues.`created_at` creation_time, issue_comments.`created_at` comment_time, u3.login commenter, " +
                "IFNULL(u1.login,u2.login) creator, !ISNULL(iass.event_id) assigned, !ISNULL(isub.event_id) subscribed from pull_requests inner join pull_request_refs on pull_request_refs.pr_id = pull_requests.id " +
                "and pull_request_refs.`issue_id` IS NOT NULL left join issues on pull_request_refs.issue_id = issues.id left join users u1 on u1.id = issues.`reporter_id` left join pull_requests pr2  on " +
                "issues.`pull_request_id` = pr2.id left join pull_request_history on pr2.id = pull_request_history.`pull_request_id` and pull_request_history.action=\"opened\"  left " +
                "join users u2 on u2.id = pull_request_history.`actor_id` left join issue_events iass on iass.actor_id = pull_requests.user_id and iass.action = \"assigned\" and iass.`issue_id` = " +
                "pull_request_refs.`issue_id` left join issue_events isub on isub.actor_id = pull_requests.user_id and isub.action = \"subscribed\" and isub.`issue_id` = pull_request_refs.`issue_id` " +
                "left join issue_comments on issue_comments.issue_id = pull_request_refs.`issue_id` left join users u3 on issue_comments.`user_id` = u3.id where pull_requests.base_repo_id = $repoId"



        def issues = [:]
        sql.eachRow(query) {
            Issue i = issues[it.issue_id]
            if (!i) {
                i = new Issue(it)
                issueMap[it.issue_id] = i
            }
            i.addComments(it)

            if (!issueMap[(i)]) {
                issueMap[(i)] = []
            }

            PullRequest req = prMap[it.pr_id]
            if (req) {

                issueMap[(i)] << req
                req.issues << i

                if (it.subscribed) {
                    req.subscribedToReferenced << i
                }

                if (it.assigned) {
                    req.assignedToReferenced << i
                }

                if (it.type == "CM_BODY") {
                    req.refsFromCommits << i
                } else {
                    req.refsFromPulls << i
                }
            }

        }


    }

    def addPullRequestComments() {
        def query = "select pull_requests.id, count(comment_id) comments, group_concat(distinct users.login) commenters from issues inner join pull_requests on pull_requests.id = issues.pull_request_id " +
                "left join issue_comments on issues.id = issue_comments.issue_id inner join users on issue_comments.user_id = users.id where pull_requests.base_repo_id = $repoId group by pull_requests.id"

        sql.eachRow(query) {
            PullRequest req = prMap[it.id]
            if (req) {
                req.prIssueComments = it.comments
                if (it.commenters) req.prIssueCommenters = it.commenters.tokenize(",") as Set
            }
        }

        query = "select pull_requests.id, count(comment_id) comments, group_concat(distinct users.login) commenters from pull_requests left join pull_request_comments on pull_requests.id = pull_request_comments.pull_request_id " +
                "left join users on pull_request_comments.user_id = users.id where pull_requests.base_repo_id = $repoId group by pull_requests.id"

        sql.eachRow(query) {
            PullRequest req = prMap[it.id]
            if (req) {
                req.prCodeComments = it.comments
                if (it.commenters) {
                    req.prCodeCommenters = it.commenters.tokenize(",") as Set
                }
            }
        }


    }

    def processAll() {

        //getCommitsFromRepo()


        getPullRequests()


        log.info("Repo $repoId: Got prmap size ${prMap.size()}")
        log.info("Looking for external merge")
        checkExternalMerge()
        log.info("Done")

        log.info("Linking to issues...")
        linkToReferencedIssues()
        log.info("Done")
        log.info("Adding PR comments...")
        addPullRequestComments()
        log.info("Done")
        log.info("Adding issue comments prior to pull...")
        markupIssueCommentsBeforePull()
        log.info("Done")
        log.info("Adding misc issue events...")
        markupIssueEvents()
        log.info("Done")
        log.info("Checking for previously created issues...")
        markupCreatedIssues()
        log.info("Done")
        log.info("Markup commit comments...")
        markupCommitComments()
        log.info("Done")
    }


    def printPullReqestInfo() {



        def headers = [
                "repo",   // * -- repo = The id of this project
                "owner",  // * -- owner = Login name of the person submitting the pull request
                "pr",     //* -- pr = A unique id for the pull_request (id col in the MSR mysql db)
                "merged",     // * -- merged = whether or not this pr was merged
                "linked",    //* -- linked = whether or not we identified something a link to a real issue
                "ownerCreated", // * -- sameOwner = whether or not ANY of the linked issues were posted by the pr owner
                "ownerCommentsB4", //  * -- commentsB4 = Whether there were comments by the owner on the issue before the PR
                "commentsB4", // * -- ownerCommentsB4 = Whether there were comments on the issue before the PR
                "subscribed", // * -- whether the owner was subscribed to this issue
                "assigned", // * -- whether the owner was assigned to this issue
                "prComments", // whether there were comments on the pull request (or code review comments)
                "otherIssueComments", // whether the pull request owner commented on other issues prior to submitting the pull request
                "otherPRComments", // whether the pull request owner commented on other pull requests prior to submitting the pull request
                "otherSubscribed", //  •	Whether the pull request owner was subscribed to other issues prior to submitting the pull request
                "otherAssigned", //  •	Whether the pull request owner was assigned to other issues prior to submitting the pull request
                "otherClosed", //  •	Whether the pull request owner closed other issues/pull requests prior to submitting the pull request
                "otherReopened", //  •	Whether the pull request owner reopened other issues/pull requests prior to submitting the pull request
                "otherMerged", // •	Whether the pull request owner merged other pull requests prior to submitting the pull request
                "referenced", // •	Whether the pull request owner referenced other issues/pull requests from a commit prior to submitting the pull request
                "mentioned", //  •	Whether the pull request owner was @mentioned in an issue/pull request
                "otherCreated", //  •	Whether the pull request owner created other issues before the pull request
                "otherPROpened", // owner previously opened others
                "commitComments", //owner previously commented on commits

                //some additional info not in the original paper
                "firstPr", //whether or not this was the first pull request on this repo for this user
                "mergedInGitHub",  // was this merged via github
                "mergedByCommitInMaster", // PR commit showed up in master
                "mergedByFixInCommit", // Github convention "fixes|closes" with PR nunber in commit message
                "mergedByShaInComment", //Last comments of PR contain commit number
                "mergedByMergeInComment", //Last comment contains language about merging
                "size_files", //* -- size_files = number of files touched (just commits authored by the pull owner)
                "size_additions", //* -- size_additions = number of total additions (just commits authored by the pull owner)
                "size_deletions", // * -- size_deletions = number of total deletions  (just commits authored by the pull owner)
                "size_cpf", // (for convenience) avg number of changes per file
                "codeReviewComments", //whether this pr had code review comments
                "prBodyComments", //whether the body of this pr had comments
                "linkedToPull", // whether one of the linked issues is a pull request
                "linkFromPullBody", // whether the link was found in the body of the pull request
                "linkFromCommitBody", //whether the link was found in the body of a commit by request author (attached to pull)
                "projectOwnerIssueComments", // whether the project owner commented on any referenced issues before the pull request
                "isProjectOwner" //whether the pull request owner is a member of the project

        ]

        def projectOwners = GitHubDbUtils.getProjectOwners(sql, repoId)

        new File("GitHubStats.${repoId}.csv").withWriter { out ->
            out.println(headers.join(","))


            prMap.values().each { PullRequest pr ->

                def line = []
                def resolved = !pr.issues.isEmpty()

                // * -- repo = The id of this project
                line << repoId

                //owner
                line << pr.owner

                //pr
                line << pr.id as String

                //merged  -- whether or not this PR was merged
                line << (pr.merged ? 1 : 0)

                //linked
                line << (resolved ? 1 : 0)

                //ownerCreated
                line << (resolved ? (pr.issues.find { Issue i ->
                    i.issueOwner == pr.owner
                } ? 1 : 0) : "")

                //ownerCommentsB4
                line << (resolved ? (pr.issues.find { Issue is ->
                    is.comments.find { Comment c ->
                        c.owner == pr.owner && (c.time < pr.createDate)
                    }
                } ? 1 : 0) : "")

                //commentsB4
                line << (resolved ? (pr.issues.find { Issue is ->
                    is.comments.find { Comment c ->
                        c.time < pr.createDate
                    }
                } ? 1 : 0) : "")

                //subscribed
                line << (resolved ? (pr.subscribedToReferenced ? 1 : 0) : "")

                //assigned
                line << (resolved ? (pr.assignedToReferenced ? 1 : 0) : "")

                //prComments
                line << ((pr.prCodeComments + pr.prIssueComments) > 0 ? 1 : 0)

                //otherIssueComments
                line << (pr.ownerCommentedOnIssueBefore ? 1 : 0)

                //otherPRComments
                line << (pr.ownerCommentedOnPullBefore ? 1 : 0)

                //otherSubscribed
                line << (pr.ownerSubscribedToOthers ? 1 : 0)

                //otherAssigned
                line << (pr.ownerAssignedToOthers ? 1 : 0)

                //otherClosed
                line << (pr.ownerClosedOtherIssues || pr.ownerClosedOtherPulls ? 1 : 0)

                //otherReopened
                line << (pr.ownerReopenedOthers ? 1 : 0)

                //otherMerged
                line << (pr.ownerMergedOthers ? 1 : 0)

                //referenced
                line << (pr.ownerReferencedOthers ? 1 : 0)

                //mentioned
                line << (pr.ownerMentionedInOthers ? 1 : 0)

                //otherCreated
                line << (pr.ownerCreatedOtherIssues ? 1 : 0)

                //otherPROpened
                line << (pr.nthRequestThisRepo > 0 ? 1 : 0)

                //commitComments
                line << (pr.ownerCommentedOnCommits ? 1 : 0)

                // "mergedInGitHub",  // was this merged via github
                line << (pr.gitHubMerger ? 1 : 0)

                // "mergedByCommitInMaster", // PR commit showed up in master
                line << (pr.mergedByCommitsInMaster ? 1 : 0)
                // "mergedByFixInCommit", // Github convention "fixes|closes" with PR nunber in commit message
                line << (pr.mergedByFixesInCommit ? 1 : 0)

                // "mergedByShaInComment", //Last comments of PR contain commit number
                line << (pr.mergedByShaInComments ? 1 : 0)

                // "mergedByMergeInComment", //Last comment contains language about merging
                line << (pr.mergedByMergedInComments ? 1 : 0)

                //* -- size_files = number of files touched
                line << pr.files ?: 0
                //* -- size_additions = number of total additions
                line << pr.additions ?: 0
                //* -- size_deletions = number of total deletions
                line << pr.deletions ?: 0
                //* -- size_cpf =  (for convenience) avg number of changes per file
                line << pr.cpf ?: 0

                //codeReviewComments
                line << (pr.prCodeComments > 0 ? 1 : 0)

                //prBodyComments
                line << (pr.prIssueComments > 0 ? 1 : 0)

                //linkedToPull
                line << (resolved ? (pr.issues.find { Issue is ->
                    is.isPull

                } ? 1 : 0) : "")

               // "linkFromPullBody", // whether the link was found in the body of the pull request
               line << (resolved ? (pr.refsFromPulls?1:0) :"")

               // "linkFromCommitBody", //whether the link was found in the body of a commit by request author (attached to pull)
                line << (resolved ? (pr.refsFromCommits?1:0) :"")

                //projectOwnerIssueComments
                line << (resolved ? (pr.issues.find { Issue is ->
                    is.comments.find { Comment c ->
                        (c.owner in projectOwners) && c.time < pr.createDate
                    }
                } ? 1 : 0) : "")

                //isProjectOwner
                line << (pr.owner in projectOwners ? 1 : 0)

                //log.info(line)
                out.println(line.join(","))

            }


        }


    }

    public static List getProjects(Sql s) {
        def q = "select selectedProjects.id, selectedProjects.url, users.login as owner, selectedProjects.name as repo, selectedProjects.numMembers from users INNER JOIN " +
                "(select projects.id, projects.url, projects.owner_id, projects.name, criteria.numMembers from projects INNER JOIN " +
                "(select usingIssues.repo_id, enoughMembers.numMembers from " +
                "(select distinct(repo_id) from issues where pull_request = 0) as usingIssues INNER JOIN " +
                "(select repo_id, count(1) as numMembers from project_members group by repo_id) as enoughMembers ON usingIssues.repo_id = enoughMembers.repo_id where enoughMembers.numMembers > 10) as criteria " +
                "ON criteria.repo_id = projects.id where forked_from is NULL) as selectedProjects " +
                "on users.id = selectedProjects.owner_id;"

        def result = []
        s.eachRow(q) {
            result << it.id
        }
        result
    }

    public static void main(String[] args) {
        //107534
        Sql s = GitHubDbUtils.getMysqlConnection()
        def skip = [1]
        getProjects(s).each { repo ->
            if (skip.contains(repo)) {
                def miner = new GithubMiner(s, repo)
                miner.processAll()
                miner.printPullReqestInfo()
            }
        }

//        def miner = new GithubMiner(s, 1)
//        miner.processAll()
//        miner.printPullReqestInfo()
    }


}
