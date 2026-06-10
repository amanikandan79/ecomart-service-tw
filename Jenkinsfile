pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test') {
            steps {
                sh './gradlew clean build --no-daemon'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            }
        }
    }
}
