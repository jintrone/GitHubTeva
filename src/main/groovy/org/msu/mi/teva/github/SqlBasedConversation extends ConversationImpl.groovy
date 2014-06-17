package org.msu.mi.teva.github

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import edu.mit.cci.adapters.csv.DiscussionThreadImpl
import edu.mit.cci.adapters.csv.PostImpl
import edu.mit.cci.teva.engine.TevaParameters
import edu.mit.cci.teva.model.Conversation
import edu.mit.cci.teva.model.ConversationImpl
import edu.mit.cci.teva.model.DiscussionThread
import edu.mit.cci.teva.model.Post
import edu.mit.cci.teva.util.TevaUtils
import edu.mit.cci.text.preprocessing.Tokenizer
import groovy.sql.Sql
import org.msu.mi.teva.github.util.GitHubApiUtils

/**
 * Created by josh on 1/20/14.
 */
class SqlBasedConversation extends ConversationImpl {

    public static enum Column {
        ID, REPLY, CREATED_TS,CREATED_S, AUTHOR, TEXT, THREAD
    }

    /**
     *
     * @param corpusName
     * @param s
     * @param query Query should guarantee that the rows are in order
     * @param cols
     */
    SqlBasedConversation(String name, Sql s, String query, Map cols) {
        super(name)
        read(s, query, cols)
    }

    def read(Sql s, String query, Map cols) {

        Map<String, DiscussionThread> threads = [:]
        s.eachRow(query) { it ->

            def d = cols[Column.CREATED_TS]? new Date(((java.sql.Timestamp)it[cols[Column.CREATED_TS]]).getTime()):new Date((it[cols[Column.CREATED_S]] as Long) *1000l)
            Post p = new PostImpl(it[cols[Column.ID]] as String, null, it[cols[Column.AUTHOR]], d)
            p.setContent(it[cols[Column.TEXT]])
            String thread = it[cols[Column.THREAD]] as String;
            if (!threads[thread]) {
                threads[thread] = new DiscussionThreadImpl(thread)
            }
            p.replyToId = ((!cols[Column.REPLY]) ? (threads[thread].posts?threads[thread].posts.last().postid:null) : it[cols[Column.REPLY]]) as String
            threads[thread].addPost(p)


        }
        threads.each { k, v ->
            addThread(v)
        }
    }



    def static populateDb(Conversation c, Sql s, String table, Map cols,Tokenizer<String> tokenizer = null) {
        List<Post> p = TevaUtils.getAllSortedPosts(c)
        def insert = "insert into ${table} (${cols[Column.ID]},${cols[Column.REPLY]},${cols[Column.CREATED_TS]},${cols[Column.AUTHOR]},${cols[Column.TEXT]},${cols[Column.THREAD]}) values (?,?,?,?,?,?)"
        p.each {
            if (it.content == null) {
                println "fuck"
            }
           def body = tokenizer?tokenizer.tokenize(it.content).join(","):it.content
            try {
                s.execute(insert,[it.postid,it.replyToId,it.time,it.userid,body,it.threadid])
            } catch (MySQLIntegrityConstraintViolationException ex) {
                //don't worry too much for now
                System.err.println("Constraint integrity error, comment id: "+it.postid)
            }
        }
    }

    public static void main(String[] args) {
        TevaParameters tevaParams = new TevaParameters(System.getResourceAsStream("/github.teva.properties"));
        Conversation c = GitHubRunner.getRemoteBootstrapRunner().conversation
        GitHubApiUtils.addIssueComments(c)
        GitHubTevaFactory factory = new GitHubTevaFactory(tevaParams,c)

        def cols = new HashMap(GitHubRunner.LOCAL_COLS)
        cols.put(Column.TEXT,"body")
        cols.put(Column.REPLY,"reply")

        def cnx =  'jdbc:mysql://localhost:3306/github-local'
        Sql s = Sql.newInstance(cnx,"root","lji123","com.mysql.jdbc.Driver")
        populateDb(c,s,"bootstrap_3",cols,new GithubTokenizer(factory.getMungers()))

    }
}
