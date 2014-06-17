package org.msu.mi.teva.github.util

import edu.mit.cci.util.ConsoleBase
import edu.mit.cci.util.ConsoleDocumentation
import edu.mit.cci.util.U
import groovy.sql.Sql
import la.io.IO
import la.matrix.Matrix
import ml.clustering.Clustering
import ml.clustering.NMF
import ml.options.NMFOptions
import ml.utils.Matlab
import textProcessor.TextProcessor

import javax.swing.JFileChooser

/**
 * Created by josh on 5/9/14.
 */
class GithubTopicUtils extends ConsoleBase {


    @ConsoleDocumentation(value = "Build a doc set for analysis")
    public void runGenerateDocset() {
        File f = U.getAnyFile("Select target directory", ".", JFileChooser.DIRECTORIES_ONLY)
        boolean includeComments = askUser("Include comments?","y") == "y"
        Sql s = GitHubDbUtils.getMysqlConnection()
        Map result = GitHubDbUtils.getMainProjects(s)
        println "$result\n"
        Long l = askUser("Enter an id: ") as Long
        GitHubDbUtils.getIssueDocSet(s, l, f, includeComments)



    }

    @ConsoleDocumentation(value = "Create doc term matrix")
    public void runCreateDocTermMatrix() {
        File f = U.getAnyFile("Select target directory", ".", JFileChooser.DIRECTORIES_ONLY)
        TextProcessor tp = new TextProcessor(f.parent, "", f.name, "txt", true, true)
        tp.verbose = true
        tp.process()
        tp.SaveResults()


    }

    @ConsoleDocumentation(value = "Run clustering")
    public void runClustering() {

        File f = U.getAnyFile("Select data directory", ".", JFileChooser.DIRECTORIES_ONLY)
        Integer clusters = askUser("Number of clusters? ", "2") as Integer
        List docs = []
        List terms = []
        new File(f, "LabelIDMap.txt").splitEachLine(/\s/) {
            docs << it[0]
        }
        new File(f,"WordList.txt").eachLine {
            terms << it.trim()
        }
        Matrix data = IO.loadMatrixFromDocTermCountFile(new File(f, "DocTermCount.txt").absolutePath)
//        KMeansOptions options = new KMeansOptions();
//        options.nClus = 10;
//        options.verbose = true;
//        options.maxIter = 100;
//
//        KMeans KMeans = new KMeans(options);
//
//        KMeans.feedData(data);
//        KMeans.initialize(null);
//        KMeans.clustering();
//        Matrix G0 = KMeans.getIndicatorMatrix();

        NMFOptions NMFOptions = new NMFOptions();
        NMFOptions.nClus = clusters;
        NMFOptions.maxIter = 100;
        NMFOptions.verbose = true;
        NMFOptions.calc_OV = false;
        NMFOptions.epsilon = 1e-5;
        Clustering NMF = new NMF(NMFOptions);

        NMF.feedData(data);
// NMF.initialize(null);
        NMF.clustering(null); // If null, KMeans will be used for initialization

        println "input number of terms " + NMF.data.rowDimension
        println "input number of documents" + NMF.data.columnDimension

        System.out.println("Basis Matrix:");
        println "indicator number of docs " + NMF.centers.columnDimension
        println "indicator number of clusters" + NMF.centers.rowDimension

        System.out.println("Indicator Matrix:");
        println "indicator number of cols " + NMF.indicatorMatrix.columnDimension
        println "indicator number of rows" + NMF.indicatorMatrix.rowDimension

        println "Doc key size: ${docs.size()}"

        Map result = extractClusters(NMF.centers, docs)

        new File(f,"clustering.txt").withWriter { out ->
            result.each {

                out.println "${it.key},${it.value.join(",")}"
            }

        }

        Map definitions =  extractClusterDefinitions(NMF.indicatorMatrix,terms)
        new File(f,"clusterdefs.txt").withWriter{ out ->
            definitions.each {
                out.println "${it.key},${it.value.collect { "${it.term}:${it.weight}" }.join(",")}"
            }

        }

    }

    @ConsoleDocumentation(value = "Explore clusters")
    public void runClusterExplorer() {
        File f = U.getAnyFile("Select data directory", ".", JFileChooser.DIRECTORIES_ONLY)
        String fname = askUser("Clustering file to analyze?", "clustering.txt")
        String dname = askUser("Clustering defintions file?","clusterdefs.txt")
        Sql sql = GitHubDbUtils.getMysqlConnection()
        Map result = GitHubDbUtils.getMainProjects(sql)
        println "$result\n"


        List results = []
        Map defs = [:]
        new File(f, fname).splitEachLine(",") {
            results << [it[0], it.tail()]
        }
        results.sort {
            return -it[1].size()
        }

        new File(f, dname).splitEachLine(",") {
            defs[it[0]] = it.tail()
        }




        def explore = true
        while (explore) {

            results.eachWithIndex { val, idx ->
                println "$idx.${val[1].size()} issues ${defs[val[0]][0..20]}"

            }

            int i  = askUser("Enter a cluster (index) to explore: ") as int
            def issuedData = results[i][1].collect {
                GitHubDbUtils.getIssueData(sql,it as Long)
            }.sort(true) { IssueData data ->
                data.created
            }

           println "Cluster: ${results[i][0]}"
           println "Definition: ${defs[results[i][0]][0..20]}"
           issuedData.each {
               it.dump(System.out)
           }

            explore = ("y" == askUser("Continue?","y"))


        }


    }

    public static Map extractClusters(Matrix indicator, List docs) {
        def result = [:]
        docs.eachWithIndex { val, int idx ->
            def cluster = Matlab.max(indicator.getColumnVector(idx))[1] as Integer
            if (result[cluster] == null) {
                result[cluster] = [val]
            } else {
                result[cluster] << val
            }

        }
        result
    }

    public static Map extractClusterDefinitions(Matrix indicator, List terms) {
        def result = [:]
        (0..<(indicator.getColumnDimension())).each {
            def weights = []
            la.vector.Vector v =  indicator.getColumnVector(it)
            (0..<(v.dim)).each { idx ->
                weights << [term:(terms[idx]),weight:(v.get(idx) as Float)]

            }
            weights.sort {
                -it.weight
            }
            result[(it)] = weights
        }
        result
    }

    public static void main(String[] args) {
        new GithubTopicUtils().start()
    }


}
