package org.msu.mi.teva.github.util

import groovy.sql.Sql

/**
 * Created by josh on 3/19/14.
 */
class GitHubLexicalUtils {

    static issueWords = ["issue","fix","address","close"]
    static def issueMatcher = ~/https:\/\/github\.com\/(\S+)\/(\S+)\/issues\/(\d+)/
    static def numberMatcher = ~/(?:issue|fix|address|close).*(?:[\s,\.\?!]+|\G)(\d+)[[\s,\.\?!]]+/

    static Map parseIssueIds(String s, Sql q = null) {

        q = q?:GitHubDbUtils.mysqlConnection
        Map result = [:]
        def m = issueMatcher.matcher(s)
        m.each {
            result+=[(it[3] as Integer):GitHubDbUtils.getProjectIdForNewName(q,it[1],it[2])]
        }
//        m.each {
//            println "Subtract ${it[0]}"
//            s-=it[0]
//        }
//
//        m = numberMatcher.matcher(s)
//        m.each {
//            result+=[(it[1] as Integer):null]
//        }
        result


    }

    static nmfClusterer() {}

}
