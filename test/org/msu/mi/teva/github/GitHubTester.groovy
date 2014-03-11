package org.msu.mi.teva.github

import edu.mit.cci.teva.engine.TevaParameters
import edu.mit.cci.teva.model.Conversation
import edu.mit.cci.text.windowing.WindowingUtils
import org.msu.mi.teva.github.GitHubRunner;


/**
 * Created by josh on 1/23/14.
 */
class GitHubTester extends GroovyTestCase{

    void testLiveConversation() {
        log.info("Running...")
        TevaParameters tevaParams = new TevaParameters(System.getResourceAsStream("/github.teva.properties"));
        Conversation c = GitHubRunner.getLocalBootstrapRunner().conversation
        GitHubTevaFactory factory = new GitHubTevaFactory(tevaParams,c)
        factory.

        Date[][] result = WindowingUtils.analyzeMultipleThreadsBySize(c,factory.tokenizer,120,30)
        log.info("Got a conversation with ${c.allThreads.size()} and ${result.length} windows")



    }
}
