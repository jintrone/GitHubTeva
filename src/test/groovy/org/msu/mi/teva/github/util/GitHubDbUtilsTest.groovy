package org.msu.mi.teva.github.util



/**
 * Created by josh on 1/23/14.
 */
class GitHubDbUtilsTest extends GroovyTestCase{

   public void testParseIdsTest() {
       def testOne = '''
I posted this as a patch file several weeks ago on the issue tracker: https://github.com/TrinityCore/TrinityCore/issues/1188
Unfortunately the patch file could not be 47,35 ported to the new tracker but how about credits for the original author... :(
'''
       assert GitHubLexicalUtils.parseIssueIds(testOne) == [(1188):12, (47):null, (35):null]


   }
}
