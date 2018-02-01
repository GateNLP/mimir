import gate.mimir.search.*
import gate.mimir.search.score.*
def qNode = qParser.parse('invention')
def qExecutor = qNode.getQueryExecutor(qEngine)
def qRunner = new RankingQueryRunnerImpl(qExecutor, new BindingScorer())
Thread.sleep(10)
println qRunner.getDocumentsCount()
println qRunner.getCurrentDocumentsCount()

qRunner.getDocumentHits(1).each { 
  println "${it.documentId}: ${it.termPosition} - ${it.length}"
}

qRunner.close()