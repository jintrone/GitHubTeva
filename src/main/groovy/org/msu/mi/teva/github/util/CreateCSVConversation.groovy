package org.msu.mi.teva.github.util

import groovy.sql.Sql
import groovy.transform.TupleConstructor

import java.sql.Timestamp

/**
 * Created by josh on 6/17/14.
 */


if (args.length ==0) {
    usage()
    System.exit(-1)
}


def url = "jdbc:mysql://${args[0]}"
def s = (args[2]!=null)?Sql.newInstance(url,args[1],args[2],'com.mysql.jdbc.Driver'):(args[1]?Sql.newInstance(url,args[1],"",'com.mysql.jdbc.Driver'):Sql.newInstance(url,'com.mysql.jdbc.Driver'))

def threads  = [:] as Map<String,CThread>

s.eachRow("select issues.repo_id, concat('T.',issues.id) thread, concat('T.',issues.id) id, users.login author, issues.created_at created_ts, concat(issue_body.title,' ',issue_body.body) text from issue_body inner join issues on issue_body.ext_ref_id = issues.ext_ref_id inner join users on issues.reporter_id = users.id order by repo_id,issues.id, issues.created_at") {
//    println it.id
//    println it.author
//    println it.created_ts
//    println it.text.replaceAll("\t"," ")

    //CPost p = new CPost(it.id,null,it.author,it.created_ts,it.text.replaceAll("\t"," "))

    threads << [(it.thread):new CThread(it.repo_id,it.thread,[new CPost(it.id,null,it.author,it.created_ts,it.text.replaceAll("\"","\\\\\""))])]
}

def skipped = [] as Set

s.eachRow("select concat('T.',issue_comments.issue_id) thread, issue_comments.comment_id id, users.login author, issue_comments.created_at created_ts, issue_comment_table.comment_text text from issue_comments inner join issue_comment_table on issue_comments.comment_id = issue_comment_table.id inner join users on issue_comments.user_id = users.id order by issue_comments.issue_id, issue_comments.created_at") {
    if (!threads[it.thread]) {
        skipped << it.thread
    } else {
        def lastPostId = threads[it.thread].posts.last().id as String
       // def text =
        threads[it.thread].posts << new CPost(it.id as String, lastPostId, it.author, it.created_ts, it.text?it.text.replaceAll("\"","\\\\\""):"")
    }
}

new File("allGitHubIssues.tsv").withWriter { Writer out ->

    out.println "repo_id|thread_id|post_id|replyTo_id|created|author|text"
    threads.values().each { CThread thread ->
        thread.posts.each {
            out.println "${thread.repoId}|${thread.threadId}|${it}"
        }

    }

}


println "Skipped ${skipped.size()}"
println "$skipped"





def usage() {

    println '''

USAGE: groovy CreateCSVConversation  <CONNECTION_STRING> [USER] [PASS]

Where CONNECTION_STRING is formatted as <host>:<port>/<dbname>
USER and PASS are optional, but connection will fail if the DB does not allow anonymous
connections.
'''


}

@TupleConstructor
class CThread {
    int repoId
    String threadId
    List<CPost> posts

}

@TupleConstructor
class CPost {
    String id
    String replyTo
    String author
    Timestamp creation
    String text


    public String toString() {
        "$id|$replyTo|$author|${creation.time}|\"$text\""
    }
}


