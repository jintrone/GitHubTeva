package org.msu.mi.teva.github.util

/**
 * Created by josh on 5/13/14.
 */
public class IssueData {


    boolean merged

    public IssueData(Long id, Long gitid, String title, String body, boolean pullRequest, boolean merged, Date created, Date closed) {
        this.id = id
        this.gitid = gitid
        this.title = title
        this.body = body
        this.pullRequest = pullRequest
        this.created = created
        this.closed = closed
        this.merged = merged
    }




    Long gitid
    Long id
    String title
    String body
    boolean pullRequest

    Date created
    Date closed
    List comments = []

    def dump(PrintStream out) {
        out.println($/
ISSUE $id (GIT ID = $gitid) ${pullRequest ? "[PR - ${!merged?"not ":""}merged]":""}
----------------
    Created: $created    Closed: $closed
    Title: ${title}
    Body: $body/$)
        comments.eachWithIndex { val, idx ->
            out.println($/Comment $idx. ${val.created} by ${val.author}
${val.text}
/$)
        }
    }


}