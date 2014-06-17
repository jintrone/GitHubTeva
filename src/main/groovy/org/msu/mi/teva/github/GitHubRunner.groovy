package org.msu.mi.teva.github

import edu.mit.cci.teva.MemoryBasedRunner
import edu.mit.cci.teva.engine.CommunityModel
import edu.mit.cci.teva.engine.TevaParameters
import edu.mit.cci.teva.model.Conversation
import edu.mit.cci.teva.util.TevaUtils
import edu.mit.cci.util.U
import groovy.sql.Sql



/**
 * Created by josh on 1/20/14.
 */
public class GitHubRunner {


    public Conversation conversation

    static Map REMOTE_COLS = [(SqlBasedConversation.Column.ID):"id",(SqlBasedConversation.Column.THREAD):"thread",(SqlBasedConversation.Column.CREATED_S):"time",(SqlBasedConversation.Column.AUTHOR):"author",(SqlBasedConversation.Column.TEXT):"text"]
    static Map LOCAL_COLS = [(SqlBasedConversation.Column.ID):"id",(SqlBasedConversation.Column.THREAD):"thread",(SqlBasedConversation.Column.CREATED_TS):"time",(SqlBasedConversation.Column.AUTHOR):"author",(SqlBasedConversation.Column.TEXT):"body",(SqlBasedConversation.Column.REPLY):"reply"]

    public GitHubRunner(String connection, String user, String pass, String select,Map cols) {
        def cnx =  connection
        Sql s = Sql.newInstance(cnx,user,pass,'com.mysql.jdbc.Driver')
        conversation = new SqlBasedConversation("bootstrap",s,select,cols);
    }

    public GitHubRunner(String select,Map cols) {
        def cnx =  'jdbc:mysql://Sociotechnical.ischool.drexel.edu:3306/github2'
        Sql s = Sql.newInstance(cnx,'josh','800MITfive','com.mysql.jdbc.Driver')
        conversation = new SqlBasedConversation("bootstrap",s,select,cols);
    }




    public static GitHubRunner getRemoteBootstrapRunner() {
        def select = "select comment_id id, issue_number thread, createdAt time, owner_login author, body text from all_issue_comments where repo='twitter/bootstrap' order by thread,time"
        return new GitHubRunner(select,REMOTE_COLS)
    }

    public static GitHubRunner getLocalBootstrapRunner() {
        def select = "select * from bootstrap_3 order by thread, time"
        return new GitHubRunner('jdbc:mysql://localhost:3306/github-local',"root","lji123",select,LOCAL_COLS)
    }




    static void main(String[] args) {

        TevaParameters tevaParams = new TevaParameters(System.getResourceAsStream("/github.teva.properties"));
        Conversation c = getLocalBootstrapRunner().conversation
        GitHubTevaFactory factory = new GitHubTevaFactory(tevaParams,c)
        U.cleanDirectory(new File(tevaParams.getWorkingDirectory()))
        CommunityModel model = new MemoryBasedRunner(c,tevaParams,factory).process()
        TevaUtils.serialize(new File(tevaParams.getWorkingDirectory() + "/CommunityOutput." + c.getName() + "." + tevaParams.getFilenameIdentifier() + ".xml"), model, CommunityModel.class);












    }
}
