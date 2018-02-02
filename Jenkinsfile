pipeline {
    agent any
    //triggers {
    //    upstream(upstreamProjects: "gate-top", threshold: hudson.model.Result.SUCCESS)
    //}
    tools { 
        maven 'Maven 3.3.9' 
        jdk 'JDK1.8' 
    }
    stages {
        stage ('Build') {
            steps {
                // build the Maven parts
                sh 'mvn -e clean install'
                // build the webapp
                dir('webapp/mimir-cloud') {
                    sh './grailsw run-command cache-mimir-plugins'
                    sh './grailsw prod war'
                }
            }
        }
        stage('Document') {
            when{
                expression { currentBuild.result != "FAILED" }
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
                branch 'master'
                expression { currentBuild.result == "SUCCESS" }
            }
            steps {
                sh 'mvn -e -DskipTests source:jar javadoc:jar deploy'
                dir('webapp/mimir-web-ui') {
                    sh './gradlew publish'
                }
                dir('webapp/mimir-web') {
                    sh './grailsw publish'
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
