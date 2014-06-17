package org.msu.mi.teva.github.util

import com.gmongo.GMongo
import com.gmongo.GMongoClient
import edu.mit.cci.text.preprocessing.StopwordMunger
import groovy.sql.Sql
import groovy.util.logging.Log4j
import org.msu.mi.teva.github.GithubTokenizer

import java.sql.BatchUpdateException

/**
 * Created by josh on 3/19/14.
 */
@Log4j
class GitHubDbUtils {


    static getProjectIdForNewName(Sql c, String owner, String repo) {
        def q = "select projects.id from projects inner join users on projects.owner_id = users.`id` where projects.name = ? and users.`login` = ?"
        def r = c.firstRow(q, repo, owner)
        if (!r) {
            //log.info("Could not identify project for $owner / $repo")
            return null
        } else {
            r.id
        }
    }

    static Sql getMysqlConnection() {
        String user = System.getProperty("mysql.local.user")
        String pass = System.getProperty("mysql.local.pass")
        String host = System.getProperty("mysql.local.host")
        String db = System.getProperty("mysql.local.db")


        def cnx = "jdbc:mysql://${host}:3306/${db}"
        println "Create connection ${cnx}"
        Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')

    }

    static GMongo getMongoConnection() {
        new GMongo()
    }

    static Map getMainProjects(Sql s) {
        def result = [:]
        s.eachRow("select id, name from projects where forked_from is NULL") {
            result[it.id] = it.name
        }
        result
    }

    static IssueData getIssueData(Sql s, Long issue_id) {
        def issue_query = "select issues.issue_id, issues.created_at, max(issue_events.created_at) closed_at, issue_body.title issue_title, issue_body.body issue_body, pull_request, pull_requests.merged merged, pull_request_body.title pr_title, pull_request_body.body pr_body from issues left join issue_body on issues.ext_ref_id = issue_body.ext_ref_id left join pull_request_body on issues.pull_request_id=pull_request_body.id left join pull_requests on issues.pull_request_id=pull_requests.id left join issue_events on issues.id = issue_events.issue_id and issue_events.action like 'closed' where issues.id=$issue_id  group by issues.id" as String
        def result = s.firstRow(issue_query)
        IssueData i = new IssueData(issue_id, result.issue_id as Long, result.pull_request ? result.pr_title : result.issue_title, result.pull_request ? result.pr_body : result.issue_body, result.pull_request as boolean, result.merged as boolean, result.created_at, result.closed_at)
        def issue_comments = "select users.login, issue_comments.created_at, issue_comment_table.comment_text from issues right join issue_comments on issue_comments.issue_id = issues.id" +
                " inner join issue_comment_table on issue_comments.comment_id = issue_comment_table.id left join users on users.id = issue_comments.user_id where issues.id=${issue_id} order by issue_comments.created_at" as String
        s.eachRow(issue_comments) {
            i.comments << [author: it.login, created: it.created_at, text: it.comment_text]
        }
        i

    }

    static Map getIssueDocSet(Sql s, Long repo_id, File directory, boolean includeComments) {
        GithubTokenizer tokenizer = new GithubTokenizer(new StopwordMunger(null, true))
        def issue_comments = "select issues.id, issue_body.title, issue_body.body from issues inner join issue_body on issues.ext_ref_id = issue_body.ext_ref_id where issues.pull_request = 0 and issues.repo_id=${repo_id} union " +
                "${includeComments ? "select issues.id, issue_comment_table.comment_text body from issues right join issue_comments on issue_comments.issue_id = issues.id inner join issue_comment_table on issue_comments.comment_id = issue_comment_table.id where issues.repo_id=${repo_id} union " : ""}" +
                "select issues.id, pull_request_body.title, pull_request_body.body from issues inner join pull_requests on issues.pull_request_id = pull_requests.id inner join pull_request_body on pull_requests.id = pull_request_body.id where issues.repo_id = ${repo_id}  and issues.pull_request=1 group by pull_request_id order by id" as String
        def last_id = -1
        def currentData = []

        println "Will run: $issue_comments"
        s.eachRow(issue_comments) {
            if (it.body && !it.body.startsWith("Automatic sanity-testing:")) {
                if (it.id != last_id) {

                    if (last_id > -1) {
                        if (currentData.size() > 3) {
                            createFile(last_id, currentData, directory)
                        }
                        currentData.clear()
                    }
                }
                last_id = it.id
                currentData += tokenizer.tokenize("${it.title ?: ""} ${it.body}")
                println "${it.id} ${currentData.size()} -  $currentData"
            }

        }
        if (!currentData.empty) {
            createFile(last_id, currentData, directory)
        }

    }

    static createFile(long last_id, List data, File directory) {
        new File(directory, "${last_id}.txt").withWriter { out ->
            out.println(last_id)
            out.println(data.join(" "))
        }
    }


    static void populateReferences(Sql s) {
        String commit_comments = "select commit_comments_fixed.body, commit_comments_fixed.sha, project_id from commit_comments_fixed inner join commits on commit_comments_fixed.commit_id = commits.id"
        Sql worker = getMysqlConnection()
        def total = 0, hits = 0
        s.eachRow(commit_comments) {
            def result = GitHubLexicalUtils.parseIssueIds(it.body, worker)
            if (!result.isEmpty()) {
                hits++
                println "${it.sha}: $hits -  ${it.body}"
            }
            total++
        }

        print "found $hits issues in $total comments"

    }


    public static void populateCommits() {
        def mongo = getMongoConnection()
        def db = mongo.getDB("msr14")
        def insert = "insert into commits_from_mongo (sha,ext_ref_id,message,additions,deletions,total,files)" +
                " values (?,?,?,?,?,?,?)"
        def sql = getMysqlConnection()

        sql.connection.autoCommit = false

        sql.withBatch(insert) { ps ->
            db.commits.find().each {
                try {
                    ps.addBatch(it.sha, it._id as String, it.commit.message, it?.stats?.additions ?: 0, it?.stats?.deletions ?: 0, it?.stats?.total ?: 0, it?.files?.size() ?: 0)
                } catch (BatchUpdateException ex) {
                    println "Error on ${it._id}"
                }
            }
        }
        sql.commit()

    }

    public static void addBranches() {
        def mongo = getMongoConnection()
        def db = mongo.getDB("msr14")
        def insert = "insert into pull_request_branches (ext_ref,pull_req_id,repo,owner,branch)" +
                " values (?,?,?,?,?)"
        def sql = getMysqlConnection()
        sql.connection.autoCommit = false

        sql.withBatch(insert) { ps ->
            db.pull_requests.find().each {
                try {
                    ps.addBatch(it._id as String, it.number as Integer, it.repo, it.owner, it.base.ref)
                } catch (BatchUpdateException ex) {
                    println "Error on ${it._id}"
                }
            }
        }
        sql.commit()

    }

    public static Set extractRefs(def reponame, def owner, def text) {
        ["https://github.com/$owner/$reponame/issues/([0-9]+)", "$owner/$reponame#(\\d+)", /#(\d+)/].collect {
            def m = text =~ it
            m.collect {
                it[1]
            }
        }.flatten() as Set

    }

    public static Set getProjectOwners(Sql s, long repo_id) {
        def q = "select repo_id, group_concat(distinct users.login) users from project_members inner join users on users.id = project_members.user_id where repo_id = $repo_id group by repo_id"
        s.firstRow(q).users.tokenize(",") as Set

    }

    public static void populateIssueRefs() {
        def sql = getMysqlConnection()
        def select = "select pull_request_body.id, concat(IFNULL(title,' '),' ',IFNULL(body,' ')) text, projects.name, users.login  from pull_request_body left join pull_requests on pull_request_body.id = pull_requests.id inner join projects on pull_requests.base_repo_id = projects.id inner join users on projects.owner_id = users.id order by pull_request_body.id desc" as String
        def insert = "insert into pull_request_refs (pr_id,issue_number) values (?,?)"
        Map refs = [:]


        sql.eachRow(select) { row ->
            refs[row.id] = extractRefs(row.name, row.login, row.text)
            println refs[row.id]
        }
        sql.connection.autoCommit = false

        sql.withBatch(insert) { ps ->
            refs.each { k, v ->

                try {
                    v.each {
                        if (it.length() < 11) ps.addBatch(k as int, it as int)
                    }
                } catch (BatchUpdateException ex) {
                    println "Error on $k->$v"
                }
            }
        }
        sql.commit()

    }

    static void populateCommitRefs() {
        String select = /select pull_requests.`id`, commits.id, commits_from_mongo.`message`, commits_from_mongo.message regexp '.*\#[0-9]+.*' ref from pull_request_commits inner join pull_requests on pull_request_commits.`pull_request_id` = pull_requests.id inner join commits on commits.id = pull_request_commits.commit_id and commits.author_id = pull_requests.user_id  left join commits_from_mongo on commits_from_mongo.`ext_ref_id` = commits.`ext_ref_id` having ref > 0/
        def insert = "insert into pull_request_refs (pr_id,issue_number,type) values (?,?,'CM_BODY')"
        Sql sql = getMysqlConnection()
        Map refs = [:]


        sql.eachRow(select) { row ->
            refs[row.id] = (row.message=~/#([0-9]+)/).collect {
                it[1]

            } as Set


        }
        sql.connection.autoCommit = false

        sql.withBatch(insert) { ps ->
            refs.each { k, v ->
                println "$k=>$v"
                try {
                    v.each {
                        if (it!=null && it.length() < 11) {
                            println "$k => $it"
                            ps.addBatch(k as int, it as int)
                        }
                    }
                } catch (BatchUpdateException ex) {
                    println "Error on $k->$v"
                }
            }
        }
        sql.commit()
    }


    public static void main(String[] args) {
       // populateReferences(getMysqlConnection())
        //populateCommits()
        //populateIssueRefs()
        //addBranches()

        populateCommitRefs()
    }
}
