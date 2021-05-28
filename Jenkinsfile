pipeline {
    agent any
    //triggers {
    //    upstream(upstreamProjects: "gate-top", threshold: hudson.model.Result.SUCCESS)
    //}
    tools { 
        maven 'Maven Current'
        jdk 'JDK1.8' 
    }
    stages {
        stage ('Build') {
            steps {
                // build the Maven parts
                sh 'mvn -e clean install'
                // build the webapp
                dir('webapp') {
                    sh 'mimir-web/gradlew --console=plain clean'
                }
                dir('webapp/mimir-cloud') {
                    sh './gradlew --console=plain runCommand -Pargs=cache-mimir-plugins'
                    sh './gradlew --console=plain assemble'
                }
            }
        }
        stage('Document') {
            when{
                expression { currentBuild.currentResult != "FAILED" }
            }
            steps {
                sh 'mvn -e site'
            }
            post {
                always {
                    junit 'mimir-test/target/surefire-reports/**/*.xml'
                    //jacoco exclusionPattern: '**/gui/**,**/gate/resources/**'
                    //findbugs canRunOnFailed: true, excludePattern: '**/gate/resources/**', failedNewAll: '0', pattern: '**/findbugsXml.xml', unstableNewAll: '0', useStableBuildAsReference: true
                    warnings canRunOnFailed: true, consoleParsers: [[parserName: 'Java Compiler (javac)']], defaultEncoding: 'UTF-8', excludePattern: "**/test/**", failedNewAll: '0', unstableNewAll: '0', useStableBuildAsReference: true
                }
                //success {
                //    step([$class: 'JavadocArchiver', javadocDir: 'target/site/apidocs', keepAll: false])
                //}
            }
        }
        stage('Deploy') {
            when{
                //for some reason even though we were building the master branch this wasn't working
                //branch 'master'
                expression { currentBuild.currentResult == "SUCCESS" }
            }
            steps {
                sh 'mvn -e -DskipTests source:jar javadoc:jar deploy'
                dir('webapp/mimir-web-ui') {
                    sh './gradlew --console=plain publish'
                }
                dir('webapp/mimir-web') {
                    sh './gradlew --console=plain publish'
                }
            }
            post {
                success {
                    archiveArtifacts 'webapp/mimir-cloud/build/libs/mimir-cloud-*.war'
                }
            }
        }
    }
}
