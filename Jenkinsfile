pipeline {

  agent {
    docker {
      image 'cfpb/jenkinsfile:scala'
    }
  }

  options {
    ansiColor('xterm')
    timestamps()
  }

  stages {

    stage('init') {
      steps {
        sh 'env | sort'
      }
    }

    stage('package') {
      steps {
        sh 'sbt clean assembly'
      }
    }

    stage('publish') {
      steps {
        archiveArtifacts (
          artifacts: "target/scala-2.12/hmda.jar"
        )
      }
    }

  }

  post {
    success {
      echo "hmda-platform built successfully!"
    }
  }

}