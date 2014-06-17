package org.msu.mi.teva.github.util

import edu.mit.cci.adapters.csv.PostImpl
import edu.mit.cci.teva.model.Conversation
import groovy.sql.Sql
import groovy.util.logging.Log4j
import groovyx.net.http.RESTClient
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.protocol.HttpContext



/**
 * Created by josh on 2/7/14.
 */
@Log4j
class GitHubApiUtils {

    static boolean cont = false

    def static pause() {
        if (cont) return
        print("Ok to continue? [y,n,(a)ll] ")
        String line = new Scanner(System.in).nextLine()
        if ("n" == line) {
            System.exit(-1)
        } else if ("a" == line) {
            cont = true
        }
    }



    def static addIssueComments(Conversation c) {
        String user = System.getProperty("mysql.remote.username")
        String pass = System.getProperty("mysql.remote.pass")
        String host = System.getProperty("mysql.remote.host")
        String db = System.getProperty("mysql.remote.db")
        def cnx = "jdbc:mysql://${host}:3306/${db}"
        Sql s = Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')
        def sql = "select sys_last_updated thread, sys_created_at time, comments author, sys_events_added text from issues where gitHubId='twitter/bootstrap'"
        def threadmap = [:]
        s.eachRow(sql) {
            PostImpl p = new PostImpl(it.thread as String, null, it.author as String, new Date(it.time * 1000l))
            p.threadId = it.thread as String
            def content = (it.text as char[]) as String
            p.content = (content == "null" ? "" : content)
            threadmap[p.threadId] = p

        }
        c.allThreads.each {
            if (threadmap[it.threadId]) {

                it.posts.add(0, threadmap[it.threadId])
                it.posts[1].replyToId = it.threadId
            } else {
                log.info("Couldn't identify issue comments for " + it.threadId)
            }
        }
        c
    }


    def static dumpHeaders(result) {
        result.headers.each {
            println "${it.name} = ${it.value}"
        }
    }

    /**
     * Call the github api to get all comments for a user / repo
     * @param rest
     * @param user
     * @param repo
     * @return
     */
    static Map getIssueComments(RESTClient rest, String user, String repo) {

        def result = [:]
        result.getMetaClass().ratelimited = { false }
        def p = 0
        while (true) {
            log.info("Getting page ${++p} repo ${user}/${repo}")

            def response = rest.get([path: "/repos/${user}/${repo}/issues/comments",
                    query: [per_page: 100, page: p],
                    headers: ["User-Agent": "jintrone-comment-slurper"]])
            dumpHeaders(response)
            result += response.data.collectEntries {
                [it.id, it.body]
            }

            if (response.headers.'X-RateLimit-Remaining' == "1") {
                def reset = new Date((response.headers.'X-RateLimte-Remaining' as Long) * 1000l)
                String msg = "Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}"
                log.warn("Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}")
                new File("RateLimited.txt").withWriter {
                    it.println "Owner:${user}"
                    it.println "Repo:${repo}"
                    it.println "LastPageRead:${p}"
                    it.println "Reset:${reset}"
                }
                result.metaClass.ratelimited = { true }
                break
            }

            if (!response.headers.'Link' || !response.headers.'Link'.contains(/; rel="last"/)) {
                break
            }
        }
        result
    }

    def static RESTClient getGitHubRestClient() {
        def rest = new RESTClient("https://api.github.com")
        String user = System.getProperty("github.user")
        String pass = System.getProperty("github.pass")

        if (!user || !pass) {
            log.warn("No user or password set - not using authentication. Rate limited to 60 requests per hour")
        }
        rest.client.addRequestInterceptor(new HttpRequestInterceptor() {
            void process(HttpRequest httpRequest, HttpContext httpContext) {
                httpRequest.addHeader("Authorization", "Basic ${"${user}:${pass}".bytes.encodeBase64().toString()}")
            }
        })
        rest


    }

    def static List getTransformedProjects(Sql sql) {
        def getProjects = "select pull_requests.base_repo_id r_id, users.login owner, projects.`name` repo_name from pull_requests inner join projects on projects.id=pull_requests.base_repo_id inner join users on projects.`owner_id` = users.id group by r_id order by r_id"
        sql.rows(getProjects).collect() {
            if (it.owner == "robey" && it.repo_name == "kestrel") {
                [repo_id: it.r_id, owner: "twitter", repo_name: it.repo_name]
            } else {
                [repo_id: it.r_id, owner: it.owner, repo_name: it.repo_name]
            }
        }
    }

    static Map getRepoPullRequests(RESTClient rest, String user, String repo) {
        def result = [:]
        result.getMetaClass().ratelimited = { false }
        def p = 0
        while (true) {
            log.info("Getting page ${++p} repo ${user}/${repo}")

            def response = rest.get([path: "/repos/${user}/${repo}/pulls",
                    query: [per_page: 100, page: p, state: "all"],
                    headers: ["User-Agent": "jintrone-comment-slurper"]])
            dumpHeaders(response)
            result += response.data.collectEntries {
                [it.number, it.body]
            }

            if (response.headers.'X-RateLimit-Remaining' == "1") {
                def reset = new Date((response.headers.'X-RateLimit-Reset' as Long) * 1000l)
                String msg = "Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}"
                log.warn("Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}")
                new File("RateLimited.txt").withWriter {
                    it.println "Owner:${user}"
                    it.println "Repo:${repo}"
                    it.println "LastPageRead:${p}"
                    it.println "Reset:${reset}"
                }
                result.metaClass.ratelimited = { true }
                break
            }

            if (!response.headers.'Link' || !response.headers.'Link'.contains(/; rel="last"/)) {
                break
            }
        }
        result
    }

    static List getRepoCommitComments(RESTClient rest, String user, String repo) {
        def result = []
        result.getMetaClass().ratelimited = false
        def p = 0
        while (true) {
            log.info("Getting page ${++p} repo ${user}/${repo}")

            def response = rest.get([path: "/repos/${user}/${repo}/comments",
                    query: [per_page: 100, page: p, state: "all"],
                    headers: ["User-Agent": "jintrone-comment-slurper"]])
            dumpHeaders(response)
            result += response.data.collect {
                [body: it.body, id: it.id as Long, user: it.user.id as Long, path: it.path, position: it.position as Integer, line: it.line as Integer, sha: it.commit_id, time: Date.parse("yyyy-MM-dd'T'HH:mm:ss'Z'", it.created_at)]
            }

            if (response.headers.'X-RateLimit-Remaining' == "1") {
                def reset = new Date((response.headers.'X-RateLimit-Reset' as Long) * 1000l)
                String msg = "Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}"
                log.warn("Reached rate limit on page ${p} of ${user}/${repo}. Will reset at ${reset}")
                new File("RateLimited.txt").withWriter {
                    it.println "Owner:${user}"
                    it.println "Repo:${repo}"
                    it.println "LastPageRead:${p}"
                    it.println "Reset:${reset}"
                }
                System.exit(-1)
                break
            }

            if (!response.headers.'Link' || !response.headers.'Link'.contains(/; rel="last"/)) {
                break
            }
        }
        result
    }

    def static populateCommitMessages() {

    }

    def static populateCommitComments() {
        def c = setup()
        def insert = "insert into commit_comments_fixed (path,user_id,body,line,position,comment_id,sha,created_at)" +
                " values (?,?,?,?,?,?,?,?)"
        c.write.connection.autoCommit = false;
        c.projects.each {
            List result = getRepoCommitComments(c.rest, it.owner, it.repo_name);

            c.write.withBatch(insert) { ps ->
                result.each { m ->
                    ps.addBatch(m.path, m.user, m.body, m.line, m.position, m.id, m.sha, m.time)
                }
            }
            c.write.commit()


            log.info("Done processing ${it.owner}/${it.repo_name}")

        }
    }


    static Map setup() {
        String user = System.getProperty("mysql.local.user")
        String pass = System.getProperty("mysql.local.pass")
        String host = System.getProperty("mysql.local.host")
        String db = System.getProperty("mysql.local.db")

        RESTClient rest = getGitHubRestClient()
        def cnx = "jdbc:mysql://${host}:3306/${db}"
        log.info("Connection: ${cnx}")

        Sql read = Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')
        List projects = getTransformedProjects(read)
        Sql write = Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')

        [read: read, write: write, projects: projects, rest: rest]
    }


    def static populatePullRequestBodies() {

        def c = setup()
        String pid = "select id, pullreq_id pr_id from pull_requests where pull_requests.base_repo_id=? order by pr_id"
        def insert = "insert into pull_request_body values (?,?) ON DUPLICATE KEY UPDATE body=?"


        c.write.connection.autoCommit = false;
        c.projects.each {
            if (it.repo_id < 34) {
                Map idmap = c.read.rows(pid, [it.repo_id]).collectEntries { pidrow ->
                    [pidrow.pr_id, pidrow.id]
                }
                Map result = getRepoPullRequests(c.rest, it.owner, it.repo_name);
                if (result.rateLimited) {
                    log.error("Rate limited");
                    System.exit(-1);

                } else {
                    c.write.withBatch(insert) { ps ->
                        result.each { k, v ->
                            ps.addBatch(idmap[k], v, v)
                        }
                    }
                    c.write.commit()

                }
            }
            log.info("Done processing ${it.owner}/${it.repo_name}")
        }
    }


    def static populateLongIssueComments() {
        String user = System.getProperty("mysql.local.user")
        String pass = System.getProperty("mysql.local.pass")
        String host = System.getProperty("mysql.local.host")
        String db = System.getProperty("mysql.local.db")

        RESTClient rest = getGitHubRestClient()


        def cnx = "jdbc:mysql://${host}:3306/${db}"
        log.info("Connection: ${cnx}")
        Sql read = Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')
        def sql = "select projects.id, projects.name repo, users.login owner, max(issue_comments.comment_id) max_comment from issue_comments inner join issues on (issue_comments.issue_id=issues.id) inner join projects on (projects.id = issues.repo_id) inner join users on (projects.owner_id = users.id) group by projects.id order by projects.id"
        Sql write = Sql.newInstance(cnx, user, pass, 'com.mysql.jdbc.Driver')
        def update = "insert into issue_comment_table values (?,?) ON DUPLICATE KEY UPDATE comment_text=?"
        Set skipComments = getCommentsToSkip(read)
        log.info("Got ${skipComments.size()} comments to skip")
        write.connection.autoCommit = false;

        read.eachRow(sql) {
            log.info("Processing ${it.owner}/${it.repo}")
            def r_owner = it.owner
            def repo = it.repo
            if (r_owner == "robey" && repo == "kestrel") {
                r_owner = "twitter"
            }

            //this is here in case an error has caused us to stop a previous run.  Update the
            //conditional below to start from the last visited repo
            if (it.id >= 78852) {
                write.withBatch(update) { ps ->
                    getIssueComments(rest, r_owner as String, repo as String).each { k, v ->
                        int commentId = k as Integer

                        if (!skipComments.contains(commentId) && (commentId <= (it.max_comment as Integer))) {
                            ps.addBatch(commentId, v, v)
                        }
                    }
                }
                write.commit()
            }
            log.info("Done Processing ${it.owner}/${it.repo}")

        }


    }

    static Set getCommentsToSkip(Sql sql) {
        def query = "select issue_comments.comment_id from issue_comments left join issue_comment_table on (issue_comments.comment_id = issue_comment_table.id) inner join issues on (issue_comments.issue_id = issues.id) where issue_comment_table.comment_text is not NULL"
        sql.rows(query)*.comment_id as Set
    }

    def static getCommentList() {
        RESTClient rest = getGitHubRestClient()
        println(getIssueComments(rest, "plataformatec", "devise").keySet())
    }


    public static void main(String[] args) {

        //populateLongIssueComments()
        //getCommentList()
        //populatePullRequestBodies()
        populateCommitComments()

    }
}

