pipeline {
  agent any
  stages {
    stage('Build') {
      environment {
        mvn = 'Maven 3.5.0'
      }
      steps {
        sh '''mvn -version
'''
        sh 'mvn package'
      }
    }
  }
}